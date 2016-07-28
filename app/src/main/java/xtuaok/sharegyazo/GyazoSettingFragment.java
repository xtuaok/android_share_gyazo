package xtuaok.sharegyazo;

import android.annotation.SuppressLint;
import android.app.Dialog;
import android.app.DialogFragment;
import android.app.Fragment;
import android.content.DialogInterface;
import android.os.Bundle;
import android.util.Log;

public abstract class GyazoSettingFragment extends Fragment implements DialogCreatable  {
    private static final String LOG_TAG = "GyazoSettingFragment";

    private SettingsDialogFragment mDialogFragment;

    protected void showDialog(int dialogId) {
        if (mDialogFragment != null) {
            Log.e(LOG_TAG, "Old dialog fragment not null!");
        }
        mDialogFragment = new SettingsDialogFragment(this, dialogId);
        mDialogFragment.show(getChildFragmentManager(), Integer.toString(dialogId));
    }

    // should override
    public Dialog onCreateDialog(int dialogId) {
        return null;
    }

    protected void removeDialog(int dialogId) {
        // mDialogFragment may not be visible yet in parent fragment's onResume().
        // To be able to dismiss dialog at that time, don't check
        // mDialogFragment.isVisible().
        if (mDialogFragment != null && mDialogFragment.getDialogId() == dialogId) {
            mDialogFragment.dismiss();
        }
        mDialogFragment = null;
    }

    /**
     * Sets the OnCancelListener of the dialog shown. This method can only be
     * called after showDialog(int) and before removeDialog(int). The method
     * does nothing otherwise.
     */
    protected void setOnCancelListener(DialogInterface.OnCancelListener listener) {
        if (mDialogFragment != null) {
            mDialogFragment.mOnCancelListener = listener;
        }
    }

    /**
     * Sets the OnDismissListener of the dialog shown. This method can only be
     * called after showDialog(int) and before removeDialog(int). The method
     * does nothing otherwise.
     */
    protected void setOnDismissListener(DialogInterface.OnDismissListener listener) {
        if (mDialogFragment != null) {
            mDialogFragment.mOnDismissListener = listener;
        }
    }

    public void onDialogShowing() {
        // override in subclass to attach a dismiss listener, for instance
    }

    public static class SettingsDialogFragment extends DialogFragment {
        private static final String KEY_DIALOG_ID = "key_dialog_id";
        private static final String KEY_PARENT_FRAGMENT_ID = "key_parent_fragment_id";

        private int mDialogId;

        private Fragment mParentFragment;

        private DialogInterface.OnCancelListener mOnCancelListener;
        private DialogInterface.OnDismissListener mOnDismissListener;

        public SettingsDialogFragment() {
            /* do nothing */
        }

        @SuppressLint("ValidFragment")
        public SettingsDialogFragment(DialogCreatable fragment, int dialogId) {
            mDialogId = dialogId;
            if (!(fragment instanceof Fragment)) {
                throw new IllegalArgumentException("fragment argument must be an instance of "
                        + Fragment.class.getName());
            }
            mParentFragment = (Fragment) fragment;
        }

        @Override
        public void onSaveInstanceState(Bundle outState) {
            super.onSaveInstanceState(outState);
            if (mParentFragment != null) {
                outState.putInt(KEY_DIALOG_ID, mDialogId);
                outState.putInt(KEY_PARENT_FRAGMENT_ID, mParentFragment.getId());
            }
        }

        @Override
        public void onStart() {
            super.onStart();

            if (mParentFragment != null && mParentFragment instanceof GyazoSettingFragment) {
                ((GyazoSettingFragment) mParentFragment).onDialogShowing();
            }
        }

        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            if (savedInstanceState != null) {
                mDialogId = savedInstanceState.getInt(KEY_DIALOG_ID, 0);
                mParentFragment = getParentFragment();
                int mParentFragmentId = savedInstanceState.getInt(KEY_PARENT_FRAGMENT_ID, -1);
                if (mParentFragment == null) {
                    mParentFragment = getFragmentManager().findFragmentById(mParentFragmentId);
                }
                if (!(mParentFragment instanceof DialogCreatable)) {
                    throw new IllegalArgumentException(
                            (mParentFragment != null
                                    ? mParentFragment.getClass().getName()
                                    : mParentFragmentId)
                                    + " must implement "
                                    + DialogCreatable.class.getName());
                }
                // This dialog fragment could be created from non-SettingsPreferenceFragment
                if (mParentFragment instanceof GyazoSettingFragment) {
                    // restore mDialogFragment in mParentFragment
                    ((GyazoSettingFragment) mParentFragment).mDialogFragment = this;
                }
            }
            return ((DialogCreatable) mParentFragment).onCreateDialog(mDialogId);
        }

        @Override
        public void onCancel(DialogInterface dialog) {
            super.onCancel(dialog);
            if (mOnCancelListener != null) {
                mOnCancelListener.onCancel(dialog);
            }
        }

        @Override
        public void onDismiss(DialogInterface dialog) {
            super.onDismiss(dialog);
            if (mOnDismissListener != null) {
                mOnDismissListener.onDismiss(dialog);
            }
        }

        public int getDialogId() {
            return mDialogId;
        }

        @Override
        public void onDetach() {
            super.onDetach();

            // This dialog fragment could be created from non-SettingsPreferenceFragment
            if (mParentFragment instanceof ProfileSettingFragment) {
                // in case the dialog is not explicitly removed by removeDialog()
                if (((GyazoSettingFragment) mParentFragment).mDialogFragment == this) {
                    ((GyazoSettingFragment) mParentFragment).mDialogFragment = null;
                }
            }
        }
    }
}
