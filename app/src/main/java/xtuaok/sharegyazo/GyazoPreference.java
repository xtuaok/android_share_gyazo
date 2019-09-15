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

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.app.AlertDialog;
import android.app.FragmentManager;
import android.app.FragmentTransaction;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceChangeListener;
import android.preference.PreferenceFragment;
import android.preference.PreferenceManager;
import android.preference.SwitchPreference;
import androidx.appcompat.app.AppCompatActivity;

import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebView;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class GyazoPreference extends AppCompatActivity {
    private static final String LOG_TAG = "GyazoPreference";

    /* general */
    public static final String PREF_DEFAULT_PROFILE = "default_profile";
    public static final String PREF_COPY_URL = "copy_gyazo_url";
    public static final String PREF_OPEN_BROWSER = "open_url_browser";
    public static final String PREF_SHARE_TEXT = "share_url_text";
    public static final String PREF_NOTIFY_ACTION = "notification_action";
    public static final String PREF_SHOW_NOTIFICATION = "show_notification";
    public static final String PREF_AUTO_CLOSE_NOTIFICATION = "auto_close_notification";

    /* Item as button */
    private static final String PREF_SERVER_PROFILE_LIST = "server_profile_list";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);

        getFragmentManager()
                .addOnBackStackChangedListener(new FragmentManager.OnBackStackChangedListener() {
                    @Override
                    public void onBackStackChanged() {
                        shouldDisplayHomeUp();
                    }
                });

        // Display the fragment as the main content.
        getFragmentManager()
                .beginTransaction()
                .setTransition(FragmentTransaction.TRANSIT_FRAGMENT_OPEN)
                .replace(R.id.container, new GyazoPreferenceFragment())
                .commit();
        shouldDisplayHomeUp();
    }

    private void shouldDisplayHomeUp() {
        boolean canback = getFragmentManager().getBackStackEntryCount()>0;
        getSupportActionBar().setDisplayHomeAsUpEnabled(canback);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                getFragmentManager().popBackStack();
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    public static class GyazoPreferenceFragment extends PreferenceFragment {
        private ProfileManager mProfileManager;
        private int mContainerHeight;

        @Override
        public void onActivityCreated(Bundle savedInstanceState) {
            super.onActivityCreated(savedInstanceState);
            ((AppCompatActivity) getActivity()).getSupportActionBar().setTitle(R.string.title_activity_preference);
        }

        public GyazoPreferenceFragment() {
            // Required empty public constructor
        }

        @Override
        public void onResume() {
            super.onResume();
            ListPreference lp = (ListPreference) findPreference(PREF_DEFAULT_PROFILE);
            lp.setValue(mProfileManager.getDefaultProfileUUID());
            fixSwitchPrefs();
        }

        @Override
        public View onCreateView (LayoutInflater inflater,
                           ViewGroup container,
                           Bundle savedInstanceState) {
            mContainerHeight = getActivity().findViewById(android.R.id.content).getHeight();
            final View fab = getActivity().findViewById(R.id.profile_add_fab);
            final float y = fab.getY();
            if (fab.getVisibility() == View.VISIBLE) {
                fab.animate()
                        .y(mContainerHeight)
                        .setDuration(200)
                        .setListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        super.onAnimationEnd(animation);
                        fab.setVisibility(View.GONE);
                        fab.setY(y);
                    }
                });
            }
            fillProfileList();
            return super.onCreateView(inflater, container,savedInstanceState);
        }

        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            addPreferencesFromResource(R.xml.gyazo_preference);
            mProfileManager = ProfileManager.getInstance(getActivity());
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getActivity());
            /* move to profile list setting */
            Preference p = findPreference(PREF_SERVER_PROFILE_LIST);
            p.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                @Override
                public boolean onPreferenceClick(Preference preference) {
                    ProfileListFragment fragment = new ProfileListFragment();
                    getFragmentManager()
                            .beginTransaction()
                            .setTransition(FragmentTransaction.TRANSIT_FRAGMENT_OPEN)
                            .replace(R.id.container, fragment)
                            .addToBackStack(null)
                            .commit();
                    return false;
                }
            });

            /* Default profile setting */
            ListPreference lp = (ListPreference)findPreference(PREF_DEFAULT_PROFILE);
            lp.setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference preference, Object newValue) {
                    String uuid = (String) newValue;
                    mProfileManager.setDefaultProfile(uuid);
                    return true;
                }
            });

            /* fix check box */
            if (prefs.getBoolean(PREF_OPEN_BROWSER, false) &&
                    prefs.getBoolean(PREF_SHARE_TEXT, false)) {
                prefs.edit().putBoolean(PREF_SHARE_TEXT, false).apply();
            }

            /* Open Browser */
            SwitchPreference sw;
            sw = (SwitchPreference) findPreference(PREF_OPEN_BROWSER);
            sw.setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
                public boolean onPreferenceChange(Preference preference, Object newValue) {
                    SwitchPreference p2 = (SwitchPreference) findPreference(PREF_SHARE_TEXT);
                    if (newValue.toString().equals("true")) {
                        p2.setChecked(false);
                    }
                    return true;
                }
            });

            /* Share url text */
            sw = (SwitchPreference) findPreference(PREF_SHARE_TEXT);
            sw.setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
                public boolean onPreferenceChange(Preference preference, Object newValue) {
                    SwitchPreference p2 = (SwitchPreference) findPreference(PREF_OPEN_BROWSER);
                    if (newValue.toString().equals("true")) {
                        p2.setChecked(false);
                    }
                    return true;
                }
            });

            /* About */
            /* OpenSource License */
            p = findPreference("opensource_license");
            p.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                @Override
                public boolean onPreferenceClick(Preference preference) {
                    WebView webview = new WebView(getActivity());
                    webview.loadUrl("file:///android_asset/licenses.html");
                    AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
                    builder.setTitle(getString(R.string.opensource_license))
                            .setPositiveButton(getString(android.R.string.ok), null)
                            .setView(webview)
                            .show();
                    return false;
                }
            });

            /* Share it */
            p = findPreference("about");
            p.setTitle(getString(R.string.app_name) + " " + BuildConfig.VERSION_NAME);
            p.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                @Override
                public boolean onPreferenceClick(Preference preference) {
                    Intent intent = new Intent();
                    intent.setAction(Intent.ACTION_SEND);
                    intent.setType("text/plain");
                    intent.putExtra(Intent.EXTRA_TEXT, getString(R.string.share_app_text));
                    startActivity(intent);
                    return false;
                }
            });
        }

        private void fixSwitchPrefs() {
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getActivity());
            SwitchPreference sp = (SwitchPreference) findPreference(PREF_SHARE_TEXT);
            if (prefs.getBoolean(PREF_OPEN_BROWSER, false)) {
                sp.setChecked(false);
            }
            sp = (SwitchPreference) findPreference(PREF_OPEN_BROWSER);
            if (prefs.getBoolean(PREF_SHARE_TEXT, false)) {
                sp.setChecked(false);
            }
        }

        private void fillProfileList() {
            ListPreference profList = (ListPreference) findPreference(PREF_DEFAULT_PROFILE);
            Iterator iter = mProfileManager.getProfiles().iterator();
            List<String> entries = new ArrayList<>();
            List<String> values = new ArrayList<>();
            entries.add(getString(R.string.none));
            values.add("");
            while(iter.hasNext()) {
                Profile prof = (Profile) iter.next();
                entries.add(prof.getName());
                values.add(prof.getUUID());
            }
            profList.setEntries(entries.toArray(new String[entries.size()]));
            profList.setEntryValues(values.toArray(new String[values.size()]));
        }

        /*
         * FIXME: it won't work at the 1st time. (OPEN:false)
         */
        @Override
        public Animator onCreateAnimator(int transit, boolean enter, int nextAnim) {
            ViewGroup vg = (ViewGroup) getActivity().findViewById(android.R.id.content);
            int w = vg.getWidth();
            if (transit == FragmentTransaction.TRANSIT_FRAGMENT_OPEN) {
                if (enter) {
                    return ObjectAnimator.ofFloat(getView(), "x", w, 0.0f).setDuration(200);
                } else {
                    return ObjectAnimator.ofFloat(getView(), "x", 0.0f, -w).setDuration(200);
                }
            } else if (transit == FragmentTransaction.TRANSIT_FRAGMENT_CLOSE) {
                if (enter) {
                    return ObjectAnimator.ofFloat(getView(), "x", -w, 0.0f).setDuration(200);
                } else {
                    return ObjectAnimator.ofFloat(getView(), "x", 0.0f, w).setDuration(200);
                }
            }
            return super.onCreateAnimator(transit, enter, nextAnim);
        }
    }
}