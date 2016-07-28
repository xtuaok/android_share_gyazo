package xtuaok.sharegyazo;

import android.os.Parcelable;
import android.os.Parcel;

import java.util.UUID;

public class Profile implements Parcelable {
    public static final String EXTRA_PROFILE = "profile";
    public static final String EXTRA_NEW_PROFILE = "new_profile";

    private String mUUID;
    private String mName;
    private String mCgiUrl;
    private String mGyazoId;
    private String mFormat;
    private int mImageQuality;
    private long mUseCount;

    public int describeContents() {
        return 0;
    }

    public static final Parcelable.Creator<Profile> CREATOR
            = new Parcelable.Creator<Profile>() {
        public Profile createFromParcel(Parcel in) {
            return new Profile(in);
        }

        public Profile[] newArray(int size) {
            return new Profile[size];
        }
    };

    public void writeToParcel(Parcel out, int flags) {
        out.writeString(mUUID);
        out.writeString(mName);
        out.writeString(mCgiUrl);
        out.writeString(mGyazoId);
        out.writeString(mFormat);
        out.writeInt(mImageQuality);
        out.writeLong(mUseCount);
    }

    private Profile(Parcel in) {
        mUUID = in.readString();
        mName = in.readString();
        mCgiUrl = in.readString();
        mGyazoId = in.readString();
        mFormat = in.readString();
        mImageQuality = in.readInt();
        mUseCount = in.readLong();
    }
    /**
     * Constructor
     */
    public Profile(String name, String url) {
        mUUID = UUID.randomUUID().toString();
        mName = name;
        mCgiUrl = url;

        /* default */
        mImageQuality = 100;
        mFormat = "png";
        mGyazoId = "";
        mUseCount = 0;
    }

    public Profile setName(String name) {
        mName = name;
        return this;
    }

    public Profile setURL(String url) {
        mCgiUrl = url;
        return this;
    }

    public Profile setGyazoID(String id) {
        mGyazoId = id;
        return this;
    }

    public Profile setImageQuality(int quality) {
        mImageQuality = quality;
        return this;
    }

    public Profile setFormat(String format) {
        mFormat = format;
        return this;
    }

    public void use() { mUseCount++; }

    public String getName() {
        return mName;
    }
    public String getUUID() {
        return mUUID;
    }
    public String getURL() {
        return mCgiUrl;
    }
    public String getGyazoID() {
        return mGyazoId;
    }
    public String getFormat() {
        return mFormat;
    }
    public int getImageQuality() {
        return mImageQuality;
    }
    public long getUseCount() { return mUseCount; }
}
