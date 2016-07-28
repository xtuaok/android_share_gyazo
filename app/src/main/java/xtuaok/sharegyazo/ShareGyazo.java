/*
 * Copyright (C) 2016 xtuaok (http://twitter.com/xtuaok)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package xtuaok.sharegyazo;

import android.Manifest;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.content.DialogInterface;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.content.Intent;
import android.provider.Settings;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.FragmentActivity;
import android.support.v4.content.ContextCompat;
import android.util.Log;
import android.widget.Toast;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;

public class ShareGyazo extends FragmentActivity {
    private static final String LOG_TAG = "ShareGyazo";
    private static final int BUFSIZ = 4096;
    private static final int REQUEST_EXTERNAL_STORAGE = 1;
    private static ProfileManager mProfileManager;
    private Uri mUri;
    private String mUUID;
    private File mTempFile = null;

    private Profile selectProfile()
    {
        Profile profile = mProfileManager.getProfileByUUID(mUUID);
        if (profile == null) {
            profile = mProfileManager.getDefaultProfile();
        }
        return profile;
    }

    private void saveTemp() {
        Log.d(LOG_TAG, "Writing temporary file");
        // copy to cache (Security reason)
        try {
            byte[] buff = new byte[BUFSIZ];
            InputStream is = getApplicationContext().getContentResolver().openInputStream(mUri);
            mTempFile = File.createTempFile("temp", ".bin", getCacheDir());
            BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(mTempFile));
            while (is.available() > 0) {
                int r = is.read(buff, 0, BUFSIZ);
                bos.write(buff, 0, r);
            }
            bos.flush();
            bos.close();
        } catch (IOException ex) {
            Toast.makeText(ShareGyazo.this, "ShareGyazo cannot access storage.", Toast.LENGTH_LONG).show();
            Log.e(LOG_TAG, "IOError: " + ex.getMessage());
            mTempFile = null;
        }
    }

    private boolean permission() {
        if (Build.VERSION.SDK_INT < 23) { return true; }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) ==
                PackageManager.PERMISSION_GRANTED)
            return true;
        if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.READ_EXTERNAL_STORAGE)) {
            Log.d(LOG_TAG, "should show request permission rationale");
            final AlertDialog dialog = new AlertDialog.Builder(this)
                    .setView(R.layout.permission_alert_dialog)
                    .setPositiveButton(R.string.update_permission, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            ActivityCompat.requestPermissions(ShareGyazo.this, new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, REQUEST_EXTERNAL_STORAGE);
                        }
                    })
                    .setNegativeButton(null, null)
                    .create();
            dialog.setOnShowListener(new DialogInterface.OnShowListener() {
                @Override
                public void onShow(DialogInterface arg0) {
                    dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(Color.BLUE);
                }
            });
            dialog.show();
        } else {
            Log.d(LOG_TAG, "should show request permission rationale : false");
            ActivityCompat.requestPermissions(ShareGyazo.this, new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, REQUEST_EXTERNAL_STORAGE);
        }
        return false;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        Log.d(LOG_TAG, "onRequestPermissionsResult");
        switch (requestCode) {
            case REQUEST_EXTERNAL_STORAGE: {
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    process_intent();
                } else {
                    final Intent intent = new Intent();
                    intent.setAction(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                    intent.addCategory(Intent.CATEGORY_DEFAULT);
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    intent.addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY);
                    intent.addFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);
                    intent.setData(Uri.parse("package:" + getApplicationContext().getPackageName()));
                    Toast.makeText(this, getString(R.string.please_grant_storage_permission), Toast.LENGTH_LONG).show();
                    startActivity(intent);
                    finish();
                }
            }
        }
    }

    private void process_intent(){
        Profile profile = selectProfile();

        if (profile == null) {
            final ProfileSelectDialog fragment = new ProfileSelectDialog();
            fragment.show(getFragmentManager(), "profile_dialog");
        } else {
            invokeService(profile);
            finish();
        }
    }

    private void invokeService(Profile profile) {
        try {
            saveTemp();
        } catch (Exception ex) {
            Log.e(LOG_TAG, "Exception: " + ex.getMessage());
        }
        if (mTempFile == null) {
            finish();
            return;
        }
        Log.d(LOG_TAG, "Using profile: " + profile.getName());
        mProfileManager.useProfile(profile);
        Intent intent = new Intent(ShareGyazo.this, UploadService.class);
        intent.setData(Uri.fromFile(mTempFile));
        intent.putExtra("profile", profile);
        startService(intent);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mProfileManager = ProfileManager.getInstance(this);
        setContentView(R.layout.activity_share_gyazo);
        mUUID = getIntent().getStringExtra("uuid");
        mUri = getIntent().getData();
        if (mUri == null)
            mUri = Uri.parse(getIntent().getExtras().get("android.intent.extra.STREAM").toString());
        if (mUri == null) {
            finish();
            return ;
        }
        if (permission())
            process_intent();
     }

    /*
     * onProfileSelect
     */
    protected void onProfileSelect(int which, Profile profile) {
        Log.d(LOG_TAG, "onProfileSelect : " + profile.getName());
        switch (which) {
            case DialogInterface.BUTTON_POSITIVE:
                Log.d(LOG_TAG, "Set default profile: " + profile.getName());
                mProfileManager.setDefaultProfile(profile);
                break;
            case DialogInterface.BUTTON_NEGATIVE:
                break;
            default:
                break;
        }
        invokeService(profile);
    }

    public static class ProfileSelectDialog extends DialogFragment {
        private Profile mProfile = null;
        public ProfileSelectDialog() { }

        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            int choice = 0;
            final ProfileManager pm = ProfileManager.getInstance(getActivity());
            final ArrayList<Profile> profiles = (ArrayList<Profile>) pm.getProfiles().clone();
            String names[] = new String[profiles.size()];
            String last_uuid = pm.getLastUseProfileUUID();

            // default
            mProfile = profiles.get(0);
            for (int i = 0; i < profiles.size(); i++) {
                Profile profile = profiles.get(i);
                names[i] = profile.getName();
                if (profile.getUUID().equals(last_uuid)) {
                    choice = i;
                    mProfile = profile;
                }
            }

            AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
            builder.setTitle(R.string.select_profile);
            builder.setSingleChoiceItems(names, choice, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int pos) {
                    mProfile = profiles.get(pos);
                }
            });
            builder.setNegativeButton(R.string.select_just_once, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    ((ShareGyazo)getActivity()).onProfileSelect(which, mProfile);
                    dialog.dismiss();
                }
            });
            builder.setPositiveButton(R.string.select_always, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    ((ShareGyazo)getActivity()).onProfileSelect(which, mProfile);
                    dialog.dismiss();
                }
            });
            return builder.create();
        }

        @Override
        public void onStop() {
            super.onStop();
            getActivity().finish();
        }
    }
}
