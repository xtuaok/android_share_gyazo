/*
 * Copyright (C) 2013 xtuaok (http://twitter.com/xtuaok)
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

import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.EditTextPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceChangeListener;
import android.preference.PreferenceActivity;
import android.preference.PreferenceManager;

public class GyazoPreference extends PreferenceActivity {
    public static final String SHARED_PREF = GyazoPreference.class.toString();
    public static final String PREF_GYAZO_CGI    = "gyazo_cgi_url";
    public static final String PREF_GYAZO_ID     = "gyazo_cgi_id";
    public static final String PREF_COPY_URL     = "copy_gyazo_url";
    public static final String PREF_OPEN_BROWSER = "open_url_browser";
    public static final String PREF_SHARE_TEXT   = "share_url_text";
    public static final String PREF_NOTIFY_ACTION = "notification_action";

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getBaseContext());
        addPreferencesFromResource(R.xml.preference);

        EditTextPreference editTextPref;
        editTextPref = (EditTextPreference)findPreference(PREF_GYAZO_CGI);
        editTextPref.setSummary(editTextPref.getText());
        editTextPref.setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                ((EditTextPreference)preference).setSummary(newValue.toString());
                return true;
            }
        });

        editTextPref = (EditTextPreference)findPreference(PREF_GYAZO_ID);
        editTextPref.setSummary(editTextPref.getText());
        editTextPref.setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                ((EditTextPreference)preference).setSummary(newValue.toString());
                return true;
            }
        });

        if (prefs.getBoolean(PREF_OPEN_BROWSER, false) &&
                prefs.getBoolean(PREF_SHARE_TEXT, false)) {
            prefs.edit().putBoolean(PREF_SHARE_TEXT, false).commit();
        }

        CheckBoxPreference checkBoxPref;
        checkBoxPref = (CheckBoxPreference)findPreference(PREF_OPEN_BROWSER);

        checkBoxPref.setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                CheckBoxPreference p2 = (CheckBoxPreference)findPreference(PREF_SHARE_TEXT);
                if (newValue.toString().equals("true")) {
                    p2.setEnabled(false);
                } else {
                    p2.setEnabled(true);
                }
                return true;
            }
        });
        checkBoxPref = (CheckBoxPreference)findPreference(PREF_SHARE_TEXT);

        checkBoxPref.setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                CheckBoxPreference p2 = (CheckBoxPreference)findPreference(PREF_OPEN_BROWSER);
                if (newValue.toString().equals("true")) {
                    p2.setEnabled(false);
                } else {
                    p2.setEnabled(true);
                }
                return true;
            }
        });
        fixCheck();

        ListPreference listPref = (ListPreference)getPreferenceScreen().findPreference(PREF_NOTIFY_ACTION);
        listPref.setSummary(listPref.getEntry());
        listPref.setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                if(preference.getKey().equals(PREF_NOTIFY_ACTION)){
                    int value = Integer.parseInt((String)newValue);
                    switch(value){
                        case 0 : preference.setSummary(R.string.none); break;
                        case 1 : preference.setSummary(R.string.open_url); break;
                        case 2 : preference.setSummary(R.string.share_url); break;
                        default: preference.setSummary(""); return false;
                    }
                    return true;
                }
                return false;
            }
        });
    }

    private void fixCheck() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getBaseContext());;
        CheckBoxPreference checkBoxPref;
        checkBoxPref = (CheckBoxPreference)findPreference(PREF_SHARE_TEXT);
        if (prefs.getBoolean(PREF_OPEN_BROWSER, false) == true) {
            checkBoxPref.setEnabled(false);
        }
        checkBoxPref = (CheckBoxPreference)findPreference(PREF_OPEN_BROWSER);
        if (prefs.getBoolean(PREF_SHARE_TEXT, false) == true) {
            checkBoxPref.setEnabled(false);
        }
    }
}
