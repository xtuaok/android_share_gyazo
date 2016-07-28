package xtuaok.sharegyazo;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.util.ArrayList;
import java.util.HashMap;

public class ProfileManager {
    private static final String LOG_TAG = "ProfileManager";

    private static final String PRESET_URL = "http://gyazo.com/upload.cgi";
    private static final String PRESET_NAME = "Gyazo";

    private static final String PREF_PROFILES = "profile_list";
    private static final String PREF_LAST_USE_PROFILE = "last_use_profile";

    // just for migration
    private static final String PREF_GYAZO_CGI = "gyazo_cgi_url";
    private static final String PREF_GYAZO_ID = "gyazo_cgi_id";
    private static final String PREF_IMAGE_FILE_FORMAT = "image_file_format";
    private static final String PREF_IMAGE_QUALITY = "image_quality";

    private static ProfileManager mInstance = null;
    private static final float MIN_SCORE = 0.3f;

    private Context mContext;
    private String mDefaultProfileUUID = "";
    private ArrayList<Profile> mProfiles = new ArrayList<>();
    private HashMap<String, Profile> mUUIDMap = new HashMap<>();
    private long mTotalUseCount;

    public static ProfileManager getInstance(Context context) {
        if (mInstance != null) return mInstance;
        mInstance = new ProfileManager(context);
        return mInstance;
    }

    private void migrateSetting() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(mContext);
        String name = "migrated";
        String url = prefs.getString(PREF_GYAZO_CGI, null);
        if (url == null) {
            url = PRESET_URL;
            name = PRESET_NAME;
        }
        String gyazo_id = prefs.getString(PREF_GYAZO_ID, "");
        int imgType = Integer.parseInt(prefs.getString(PREF_IMAGE_FILE_FORMAT, "0"));
        int quality = Integer.parseInt(prefs.getString(PREF_IMAGE_QUALITY, "100"));
        Profile profile = new Profile(name, url);
        profile.setGyazoID(gyazo_id);
        if (imgType == 0) profile.setFormat("png");
        else profile.setFormat("jpg");
        profile.setImageQuality(quality);
        addProfile(profile);
    }

    public ProfileManager(Context context) {
        mContext = context;
        reloadProfiles();
    }

    public void addProfile(Profile profile) {
        mProfiles.add(profile);
        storeProfiles();
        mUUIDMap.put(profile.getUUID(), profile);
    }

    public void removeProfile(Profile profile) {
        mProfiles.remove(profile);
        mUUIDMap.remove(profile.getUUID());
        storeProfiles();
    }

    public void storeProfiles() {
        String json = new Gson().toJson(mProfiles);
        PreferenceManager
                .getDefaultSharedPreferences(mContext)
                .edit()
                .putString(PREF_PROFILES, json)
                .apply();
        // Log.d(LOG_TAG, "Save JSON: " + json);
    }

    public ArrayList<Profile> getProfiles () {
        return mProfiles;
    }

    public void reloadProfiles () {
        mUUIDMap.clear();
        mProfiles.clear();
        mTotalUseCount = 0;

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(mContext);
        String json = prefs.getString(PREF_PROFILES, "");
        // Log.d(LOG_TAG, "Load JSON: " + json);
        if (!json.isEmpty()) {
            mProfiles = new Gson().fromJson(json, new TypeToken<ArrayList<Profile>>() {
            }.getType());
        } else {
            migrateSetting();
        }

        for (Profile mProfile : mProfiles) {
            mUUIDMap.put(mProfile.getUUID(), mProfile);
            mTotalUseCount += mProfile.getUseCount();
        }

        mDefaultProfileUUID = prefs.getString(GyazoPreference.PREF_DEFAULT_PROFILE, "");
        if (! mDefaultProfileUUID.isEmpty() && mUUIDMap.get(mDefaultProfileUUID) == null) {
            mDefaultProfileUUID = "";
            prefs.edit().putString(GyazoPreference.PREF_DEFAULT_PROFILE, "").apply();
        }
    }

    public void useProfile(String uuid) {
        Profile profile = mUUIDMap.get(uuid);
        if (profile != null) {
            useProfile(profile);
        }
    }

    public void useProfile(Profile profile) {
        profile.use();
        storeProfiles();
        mTotalUseCount += 1;
        PreferenceManager.getDefaultSharedPreferences(mContext)
                .edit()
                .putString(PREF_LAST_USE_PROFILE, profile.getUUID())
                .apply();
    }

    public String getLastUseProfileUUID() {
        String uuid = PreferenceManager.getDefaultSharedPreferences(mContext)
                .getString(PREF_LAST_USE_PROFILE, "");
        return uuid;
    }

    public int numProfiles() {
        return mProfiles.size();
    }

    public Profile getProfileByUUID(String uuid) {
        return mUUIDMap.get(uuid);
    }

    public Profile getDefaultProfile() {
        return getProfileByUUID(mDefaultProfileUUID);
    }

    public String getDefaultProfileUUID() {
        return mDefaultProfileUUID;
    }

    public void setDefaultProfile(Profile profile) {
        setDefaultProfile(profile.getUUID());
    }

    public void setDefaultProfile(String uuid) {
        mDefaultProfileUUID = uuid;
        PreferenceManager.getDefaultSharedPreferences(mContext)
                .edit()
                .putString(GyazoPreference.PREF_DEFAULT_PROFILE, mDefaultProfileUUID)
                .apply();
    }

    public float getChooserScore(Profile profile) {
        if (mTotalUseCount == 0) return MIN_SCORE;
        float usage = (float)profile.getUseCount() / (float) mTotalUseCount;
        if (usage < MIN_SCORE) return MIN_SCORE;
        else return usage;
    }
}
