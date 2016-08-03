package xtuaok.sharegyazo;

import android.animation.Animator;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.FragmentTransaction;
import android.content.DialogInterface;
import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AppCompatActivity;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.SeekBar;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

import xtuaok.sharegyazo.item.*;
import xtuaok.sharegyazo.item.ItemListAdapter;

public class ProfileSettingFragment extends GyazoSettingFragment
    implements AdapterView.OnItemClickListener {
    private static final int MENU_SAVE = Menu.FIRST;
    private static final int MENU_REMOVE = Menu.FIRST + 1;

    private static final int DIALOG_PROFILE_NAME   = 3;

    private static final int DIALOG_URL            = 4;
    private static final int DIALOG_GYAZO_ID       = 5;

    private static final int DIALOG_IMAGE_FORMAT   = 6;
    private static final int DIALOG_IMAGE_QUALITY  = 7;

    private int mLastSelectedPosition = -1;
    private Item mSelectedItem;

    private Profile mProfile;
    private boolean mNewProfile;

    private OnFragmentInteractionListener mListener;

    private List<Item> mItems = new ArrayList<>();
    ItemListAdapter mAdapter;
    ListView mListView;

    public ProfileSettingFragment() {
        // Required empty public constructor
    }

    public static ProfileSettingFragment newInstance(Profile profile, boolean newProfile) {
        ProfileSettingFragment fragment = new ProfileSettingFragment();
        Bundle args = new Bundle();
        args.putParcelable(Profile.EXTRA_PROFILE, profile);
        args.putBoolean(Profile.EXTRA_NEW_PROFILE, newProfile);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            mProfile = getArguments().getParcelable(Profile.EXTRA_PROFILE);
            mNewProfile = getArguments().getBoolean(Profile.EXTRA_NEW_PROFILE, false);
        }
        mAdapter = new ItemListAdapter(getActivity(), mItems);
        rebuildItemList();
        setHasOptionsMenu(true);
    }

    private void rebuildItemList() {
        mItems.clear();

        // Name
        mItems.add(new Header(getString(R.string.profile_name_title)));
        mItems.add(new ProfileNameItem(mProfile));

        // Server setting
        mItems.add(new Header(getString(R.string.profile_server_settings)));
        mItems.add(new CgiUrlItem("Gyazo URL", mProfile));
        mItems.add(new GyazoIDItem("Gyazo ID", mProfile));

        // Image setting
        mItems.add(new Header(getString(R.string.profile_image_settings)));
        mItems.add(new ImageFormatItem(getString(R.string.image_format), mProfile));
        mItems.add(new QualityItem(getString(R.string.image_quality), mProfile));

        // complete
        mAdapter.notifyDataSetChanged();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_profile_setting, container, false);

        mListView = (ListView) view.findViewById(R.id.profile_setting_list);
        mListView.setOnItemClickListener(this);
        return view;
    }

    // TODO: Rename method, update argument and hook method into UI event
    public void onButtonPressed(Uri uri) {
        if (mListener != null) {
            mListener.onFragmentInteraction(uri);
        }
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        mListener = null;
    }

    @Override
    public void onDetach() {
        super.onDetach();
        mListener = null;
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        mListView.setAdapter(mAdapter);
        if (mNewProfile) {
            ((AppCompatActivity) getActivity()).getSupportActionBar().setTitle(R.string.profile_setting_title);
        } else {
            ((AppCompatActivity) getActivity()).getSupportActionBar().setTitle(mProfile.getName());
        }
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);
        menu.add(0, MENU_SAVE, 0, R.string.profile_menu_save_title)
                .setIcon(R.drawable.ic_save_white_24dp)
                .setAlphabeticShortcut('d')
                .setEnabled(true)
                .setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM |
                        MenuItem.SHOW_AS_ACTION_WITH_TEXT);
        if (!mNewProfile) {
            menu.add(0, MENU_REMOVE, 0, R.string.profile_menu_delete_title)
                    .setIcon(R.drawable.ic_delete_white_24dp)
                    .setAlphabeticShortcut('s')
                    .setEnabled(true)
                    .setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM |
                            MenuItem.SHOW_AS_ACTION_WITH_TEXT);
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        AlertDialog dialog;
        final ProfileManager pm = ProfileManager.getInstance(getActivity());
        switch (item.getItemId()) {
            case MENU_REMOVE:
                if (pm.numProfiles() > 1) {
                    dialog = new AlertDialog.Builder(getActivity())
                            .setTitle(R.string.remove_profile_title)
                            .setIcon(R.drawable.ic_warning_24dp)
                            .setMessage(getString(R.string.remove_confirm_message, mProfile.getName()))
                            .setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog, int button) {
                                    pm.removeProfile(mProfile);
                                    dialog.dismiss();
                                    Snackbar.make(getActivity().findViewById(R.id.container), R.string.toast_remove_profile, Snackbar.LENGTH_LONG).show();
                                    finishFragment();
                                }
                            })
                            .setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog, int button) {
                                    dialog.dismiss();
                                }
                            }).create();
                } else {
                    dialog = new AlertDialog.Builder(getActivity())
                            .setIcon(R.drawable.ic_warning_24dp)
                            .setTitle(R.string.remove_profile_title)
                            .setMessage(R.string.cannot_remove_profile)
                            .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener(){
                                public void onClick(DialogInterface dialog, int button) { dialog.dismiss(); }
                            })
                            .create();
                }
                dialog.show();
                return true;
            case MENU_SAVE:
                if (mNewProfile) {
                    pm.addProfile(mProfile);
                } else {
                    pm.storeProfiles();
                }
                Snackbar.make(getActivity().findViewById(R.id.container), R.string.toast_save_profile, Snackbar.LENGTH_LONG).show();
                finishFragment();
                return true;
        }
        return super.onOptionsItemSelected(item);
    }
    /**
     * This interface must be implemented by activities that contain this
     * fragment to allow an interaction in this fragment to be communicated
     * to the activity and potentially other fragments contained in that
     * activity.
     * <p>
     * See the Android Training lesson <a href=
     * "http://developer.android.com/training/basics/fragments/communicating.html"
     * >Communicating with Other Fragments</a> for more information.
     */
    public interface OnFragmentInteractionListener {
        void onFragmentInteraction(Uri uri);
    }

    public final void finishFragment() {
        getActivity().onBackPressed();
    }

    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        final Item itemAtPosition = (Item) parent.getItemAtPosition(position);
        mSelectedItem = itemAtPosition;
        mLastSelectedPosition = mAdapter.getPosition(itemAtPosition);
        if (itemAtPosition instanceof ProfileNameItem) {
            showDialog(DIALOG_PROFILE_NAME);
        } else if (itemAtPosition instanceof CgiUrlItem) {
            showDialog(DIALOG_URL);
        } else if (itemAtPosition instanceof GyazoIDItem) {
            showDialog(DIALOG_GYAZO_ID);
        } else if (itemAtPosition instanceof ImageFormatItem) {
            showDialog(DIALOG_IMAGE_FORMAT);
        } else if (itemAtPosition instanceof QualityItem) {
            showDialog(DIALOG_IMAGE_QUALITY);
        }
    }

    @Override
    public Dialog onCreateDialog(int dialogId) {
        switch (dialogId) {
            case DIALOG_PROFILE_NAME:
                return requestProfileName();
            case DIALOG_URL:
                return requestCGIUrl();
            case DIALOG_GYAZO_ID:
                return requestGyazoID();
            case DIALOG_IMAGE_FORMAT:
                return requestImageFormat();
            case DIALOG_IMAGE_QUALITY:
                return requestImageQuality();
        }
        return super.onCreateDialog(dialogId);
    }

    /* Dialogs */
    /*
     * ProfileName
     */
    private AlertDialog requestProfileName() {
        LayoutInflater inflater = LayoutInflater.from(getActivity());
        View dialogView = inflater.inflate(R.layout.text_input_dialog, null);

        final EditText entry = (EditText) dialogView.findViewById(R.id.text);
        entry.setText(mProfile.getName());
        entry.setHint(R.string.rename_dialog_hint);
        entry.setSelectAllOnFocus(true);

        final TextView prompt = (TextView) dialogView.findViewById(R.id.prompt);
        prompt.setText(R.string.rename_dialog_message);


        final AlertDialog alertDialog = new AlertDialog.Builder(getActivity())
                .setTitle(R.string.rename_dialog_title)
                .setView(dialogView)
                .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        String value = entry.getText().toString();
                        mProfile.setName(value);
                        if (value.equalsIgnoreCase("imgur")) {
                            mProfile.setURL("https://api.imgur.com/");
                        } else if (value.equalsIgnoreCase("gyazo")) {
                            mProfile.setURL("http://gyazo.com/upload.cgi");
                        }
                        mAdapter.notifyDataSetChanged();
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .create();

        entry.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {

            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {

            }

            @Override
            public void afterTextChanged(Editable s) {
                final String str = s.toString();
                final boolean empty = TextUtils.isEmpty(str)
                        || TextUtils.getTrimmedLength(str) == 0;
                alertDialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(!empty);
            }
        });
        alertDialog.setOnShowListener(new DialogInterface.OnShowListener() {
            @Override
            public void onShow(DialogInterface dialog) {
                InputMethodManager imm = (InputMethodManager)
                        getActivity().getSystemService(Context.INPUT_METHOD_SERVICE);
                imm.showSoftInput(entry, InputMethodManager.SHOW_IMPLICIT);
            }
        });
        return alertDialog;
    }

    /*
    * CGI URL
     */
    private AlertDialog requestCGIUrl() {
        LayoutInflater inflater = LayoutInflater.from(getActivity());
        View dialogView = inflater.inflate(R.layout.text_input_dialog, null);

        final EditText entry = (EditText) dialogView.findViewById(R.id.text);
        entry.setText(mProfile.getURL());
        entry.setHint(R.string.cgi_url_dialog_hint);
        entry.setSelectAllOnFocus(true);

        final TextView prompt = (TextView) dialogView.findViewById(R.id.prompt);
        prompt.setText(R.string.cgi_url_dialog_message);

        final AlertDialog alertDialog = new AlertDialog.Builder(getActivity())
                .setTitle(R.string.cgi_url_dialog_title)
                .setView(dialogView)
                .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        String value = entry.getText().toString();
                        mProfile.setURL(value);
                        mAdapter.notifyDataSetChanged();
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .create();

        entry.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {

            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {

            }

            @Override
            public void afterTextChanged(Editable s) {
                final String str = s.toString();
                final boolean empty = TextUtils.isEmpty(str)
                        || TextUtils.getTrimmedLength(str) == 0
                        || ( ! str.startsWith("http://") && ! str.startsWith("https://") );
                alertDialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(!empty);
            }
        });
        alertDialog.setOnShowListener(new DialogInterface.OnShowListener() {
            @Override
            public void onShow(DialogInterface dialog) {
                InputMethodManager imm = (InputMethodManager)
                        getActivity().getSystemService(Context.INPUT_METHOD_SERVICE);
                imm.showSoftInput(entry, InputMethodManager.SHOW_IMPLICIT);
            }
        });
        return alertDialog;
    }

    private AlertDialog requestGyazoID() {
        LayoutInflater inflater = LayoutInflater.from(getActivity());
        View dialogView = inflater.inflate(R.layout.text_input_dialog, null);

        final EditText entry = (EditText) dialogView.findViewById(R.id.text);
        entry.setText(mProfile.getGyazoID());
        entry.setHint(R.string.gyazo_id_dialog_hint);
        entry.setSelectAllOnFocus(true);

        final TextView prompt = (TextView) dialogView.findViewById(R.id.prompt);
        prompt.setText(R.string.gyazo_id_dialog_message);

        final AlertDialog alertDialog = new AlertDialog.Builder(getActivity())
                .setTitle(R.string.gyazo_id_dialog_title)
                .setView(dialogView)
                .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        String value = entry.getText().toString();
                        mProfile.setGyazoID(value);
                        mAdapter.notifyDataSetChanged();
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .create();

        alertDialog.setOnShowListener(new DialogInterface.OnShowListener() {
            @Override
            public void onShow(DialogInterface dialog) {
                InputMethodManager imm = (InputMethodManager)
                        getActivity().getSystemService(Context.INPUT_METHOD_SERVICE);
                imm.showSoftInput(entry, InputMethodManager.SHOW_IMPLICIT);
            }
        });
        return alertDialog;
    }

    private AlertDialog requestImageFormat() {
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        final String[] formatEntries =
                getResources().getStringArray(R.array.image_format_entries);
        final String[] formatValues =
                getResources().getStringArray(R.array.image_format_values);

        int defaultIndex = 0;
        for (int i = 0; i < formatValues.length; i++) {
            if (formatValues[i].equals(mProfile.getFormat())) {
                defaultIndex = i;
                break;
            }
        }

        builder.setTitle(R.string.image_format_dialog_title);
        builder.setSingleChoiceItems(formatEntries, defaultIndex,
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int item) {
                        mProfile.setFormat(formatValues[item]);
                        mAdapter.notifyDataSetChanged();
                        dialog.dismiss();
                    }
                });

        builder.setNegativeButton(android.R.string.cancel, null);

        return builder.create();
    }

    private AlertDialog requestImageQuality() {
        LayoutInflater inflater = LayoutInflater.from(getActivity());
        View dialogView = inflater.inflate(R.layout.seekbar_dialog, null);

        final TextView prompt = (TextView) dialogView.findViewById(R.id.prompt);
        prompt.setText(getString(R.string.image_quality_dialog_message, mProfile.getImageQuality()));

        final SeekBar bar = (SeekBar) dialogView.findViewById(R.id.seekbar);
        bar.setMax(100);
        bar.setProgress(mProfile.getImageQuality());

        final AlertDialog alertDialog = new AlertDialog.Builder(getActivity())
                .setTitle(R.string.image_quality_dialog_title)
                .setView(dialogView)
                .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        int value = bar.getProgress();
                        mProfile.setImageQuality(value);
                        mAdapter.notifyDataSetChanged();
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .create();

        bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                prompt.setText(getString(R.string.image_quality_dialog_message, progress));
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {

            }
        });
        return alertDialog;
    }

    @Override
    public Animator onCreateAnimator(int transit, boolean enter, int nextAnim) {
        AnimatorSet as = new AnimatorSet();
        ViewGroup vg = (ViewGroup) getActivity().findViewById(android.R.id.content);
        int h = vg.getHeight();
        if (transit == FragmentTransaction.TRANSIT_FRAGMENT_OPEN) {
            if (enter) {
                as.play(ObjectAnimator.ofFloat(getView(), View.ALPHA, 0, 1).setDuration(300))
                        .with(ObjectAnimator.ofFloat(getView(), "y", h, 0.0f).setDuration(300));
                return as;
            } else {
                as.play(ObjectAnimator.ofFloat(getView(), View.ALPHA, 1, 0).setDuration(300))
                        .with(ObjectAnimator.ofFloat(getView(), "y", 0.0f, -h).setDuration(300));
                return as;
            }
        } else if (transit == FragmentTransaction.TRANSIT_FRAGMENT_CLOSE) {
            if (enter) {
                as.play(ObjectAnimator.ofFloat(getView(), View.ALPHA, 0, 1).setDuration(300))
                        .with(ObjectAnimator.ofFloat(getView(), "y", -h, 0.0f).setDuration(300));
                return as;
            } else {
                as.play(ObjectAnimator.ofFloat(getView(), View.ALPHA, 1, 0).setDuration(300))
                        .with(ObjectAnimator.ofFloat(getView(), "y", 0.0f, h).setDuration(300));
                return as;
            }
        }
        return super.onCreateAnimator(transit, enter, nextAnim);
    }
}
