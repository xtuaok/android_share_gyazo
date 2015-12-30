/*
 * Copyright (C) 2015 xtuaok (http://twitter.com/xtuaok)
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

import android.app.Activity;
import android.app.Notification;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.EditTextPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceChangeListener;
import android.preference.PreferenceFragment;
import android.preference.PreferenceManager;
import android.text.InputFilter;
import android.text.Spanned;

public class GyazoPreference extends Activity {
    public static final String SHARED_PREF = GyazoPreference.class.toString();
    public static final String PREF_GYAZO_CGI = "gyazo_cgi_url";
    public static final String PREF_GYAZO_ID = "gyazo_cgi_id";
    public static final String PREF_COPY_URL = "copy_gyazo_url";
    public static final String PREF_OPEN_BROWSER = "open_url_browser";
    public static final String PREF_SHARE_TEXT = "share_url_text";
    public static final String PREF_NOTIFY_ACTION = "notification_action";
    public static final String PREF_SHOW_NOTIFICATION = "show_notification";
    public static final String PREF_AUTO_CLOSE_NOTIFICATION = "auto_close_notification";

    public static final String PREF_IMAGE_FILE_FORMAT = "image_file_format";
    public static final String PREF_IMAGE_QUALITY = "image_quality";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Display the fragment as the main content.
        getFragmentManager().beginTransaction().replace(android.R.id.content,
                new GyazoPreferenceFragment()).commit();
    }

    public static class GyazoPreferenceFragment extends PreferenceFragment {


        public GyazoPreferenceFragment() {
            // Required empty public constructor
        }

        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getActivity());
            addPreferencesFromResource(R.xml.preference);

            EditTextPreference editTextPref;
            editTextPref = (EditTextPreference) findPreference(PREF_GYAZO_CGI);
            editTextPref.setSummary(editTextPref.getText());
            editTextPref.setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
                public boolean onPreferenceChange(Preference preference, Object newValue) {
                    ((EditTextPreference) preference).setSummary(newValue.toString());
                    return true;
                }
            });

            editTextPref = (EditTextPreference) findPreference(PREF_GYAZO_ID);
            editTextPref.setSummary(editTextPref.getText());
            editTextPref.setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
                public boolean onPreferenceChange(Preference preference, Object newValue) {
                    ((EditTextPreference) preference).setSummary(newValue.toString());
                    return true;
                }
            });

            editTextPref = (EditTextPreference) findPreference(PREF_IMAGE_QUALITY);
            editTextPref.getEditText().setFilters(new InputFilter[]{ new InputFilterMinMax("0", "100")});
            editTextPref.setSummary(editTextPref.getText());
            editTextPref.setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
                public boolean onPreferenceChange(Preference preference, Object newValue) {
                    ((EditTextPreference) preference).setSummary(newValue.toString());
                    return true;
                }
            });

            if (prefs.getBoolean(PREF_OPEN_BROWSER, false) &&
                    prefs.getBoolean(PREF_SHARE_TEXT, false)) {
                prefs.edit().putBoolean(PREF_SHARE_TEXT, false).commit();
            }

            CheckBoxPreference checkBoxPref;
            checkBoxPref = (CheckBoxPreference) findPreference(PREF_OPEN_BROWSER);

            checkBoxPref.setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
                public boolean onPreferenceChange(Preference preference, Object newValue) {
                    CheckBoxPreference p2 = (CheckBoxPreference) findPreference(PREF_SHARE_TEXT);
                    if (newValue.toString().equals("true")) {
                        p2.setEnabled(false);
                    } else {
                        p2.setEnabled(true);
                    }
                    return true;
                }
            });
            checkBoxPref = (CheckBoxPreference) findPreference(PREF_SHARE_TEXT);

            checkBoxPref.setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
                public boolean onPreferenceChange(Preference preference, Object newValue) {
                    CheckBoxPreference p2 = (CheckBoxPreference) findPreference(PREF_OPEN_BROWSER);
                    if (newValue.toString().equals("true")) {
                        p2.setEnabled(false);
                    } else {
                        p2.setEnabled(true);
                    }
                    return true;
                }
            });
            fixCheck();
        }

        private void fixCheck() {
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getActivity());
            CheckBoxPreference checkBoxPref;
            checkBoxPref = (CheckBoxPreference) findPreference(PREF_SHARE_TEXT);
            if (prefs.getBoolean(PREF_OPEN_BROWSER, false) == true) {
                checkBoxPref.setEnabled(false);
            }
            checkBoxPref = (CheckBoxPreference) findPreference(PREF_OPEN_BROWSER);
            if (prefs.getBoolean(PREF_SHARE_TEXT, false) == true) {
                checkBoxPref.setEnabled(false);
            }
        }

        public class InputFilterMinMax implements InputFilter {

            private int min, max;

            public InputFilterMinMax(int min, int max) {
                this.min = min;
                this.max = max;
            }

            public InputFilterMinMax(String min, String max) {
                this.min = Integer.parseInt(min);
                this.max = Integer.parseInt(max);
            }

            @Override
            public CharSequence filter(CharSequence source, int start, int end, Spanned dest, int dstart, int dend) {
                try {
                    int input = Integer.parseInt(dest.toString() + source.toString());
                    if (isInRange(min, max, input))
                        return null;
                } catch (NumberFormatException nfe) { }
                return "";
            }

            private boolean isInRange(int a, int b, int c) {
                return b > a ? c >= a && c <= b : c >= b && c <= a;
            }
        }
    }
}