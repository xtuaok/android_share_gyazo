package xtuaok.sharegyazo;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.app.Fragment;
import android.app.FragmentTransaction;
import android.content.Context;
import android.os.Bundle;
import android.support.design.widget.FloatingActionButton;
import android.support.v7.app.AppCompatActivity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ListView;

import java.util.ArrayList;
import java.util.List;

import xtuaok.sharegyazo.item.Item;
import xtuaok.sharegyazo.item.ItemListAdapter;
import xtuaok.sharegyazo.item.ProfileItem;

/**
 * A fragment representing a list of Items.
 */
public class ProfileListFragment extends Fragment
    implements AdapterView.OnItemClickListener {

    private ProfileManager mProfileManager;
    private ArrayList<Profile> mList;

    private List<Item> mItems = new ArrayList<>();
    private ItemListAdapter mAdapter;
    private ListView mListView;

    private int containerHeight;
    /**
     * Mandatory empty constructor for the fragment manager to instantiate the
     * fragment (e.g. upon screen orientation changes).
     */
    public ProfileListFragment() {
    }

    // TODO: Customize parameter initialization
    @SuppressWarnings("unused")
    public static ProfileListFragment newInstance(int columnCount) {
        ProfileListFragment fragment = new ProfileListFragment();
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mProfileManager = ProfileManager.getInstance(getActivity());
        mAdapter = new ItemListAdapter(getActivity(), mItems);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        containerHeight = container.getHeight();
        final View view = inflater.inflate(R.layout.fragment_profile_list, container, false);
        final FloatingActionButton fab = (FloatingActionButton) getActivity().findViewById(R.id.profile_add_fab);
        final float y = fab.getY();

        /* ListView */
        mListView = (ListView) view.findViewById(R.id.profole_list);
        mListView.setOnItemClickListener(this);

        fab.setVisibility(View.VISIBLE);
        fab.setY(containerHeight);
        fab.animate()
                .y(y)
                .setDuration(200)
                .setListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        super.onAnimationEnd(animation);
                    }
                });

        /* FAB */
        fab.setOnClickListener(new View.OnClickListener() {
            public void onClick(View view) {
                final View v = view;
                Bundle bundle = new Bundle();
                Profile profile = new Profile("gyazo", "http://gyazo.com/upload.cgi");
                bundle.putParcelable(Profile.EXTRA_PROFILE, profile);
                bundle.putBoolean(Profile.EXTRA_NEW_PROFILE, true);
                ProfileSettingFragment fragment = new ProfileSettingFragment();
                fragment.setArguments(bundle);
                v.animate()
                        .alpha(0)
                        .setDuration(200)
                        .setListener(new AnimatorListenerAdapter() {
                            @Override
                            public void onAnimationEnd(Animator animation) {
                                super.onAnimationEnd(animation);
                                v.setVisibility(View.GONE);
                                v.setAlpha(1);
                            }
                        });
                getFragmentManager()
                        .beginTransaction()
                        .setTransition(FragmentTransaction.TRANSIT_FRAGMENT_OPEN)
                        .replace(R.id.container, fragment)
                        .addToBackStack(null)
                        .commit();
            }
        });

        rebuildItemList();
        return view;
    }


    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
    }

    @Override
    public void onDetach() {
        super.onDetach();
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        ((AppCompatActivity) getActivity()).getSupportActionBar().setTitle(R.string.profile_list_title);
        mListView.setAdapter(mAdapter);
    }

    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        final ProfileItem itemAtPosition = (ProfileItem) parent.getItemAtPosition(position);
        Profile profile = itemAtPosition.mProfile;
        final View fab = getActivity().findViewById(R.id.profile_add_fab);
        final float y = fab.getY();
        fab.animate().y(containerHeight).setDuration(200).setListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                super.onAnimationEnd(animation);
                fab.setVisibility(View.GONE);
                fab.setY(y);
            }
        });

        Bundle bundle = new Bundle();
        bundle.putParcelable(Profile.EXTRA_PROFILE, profile);
        bundle.putBoolean(Profile.EXTRA_NEW_PROFILE, false);
        ProfileSettingFragment fragment = new ProfileSettingFragment();
        fragment.setArguments(bundle);
        getFragmentManager()
                .beginTransaction()
                .setTransition(FragmentTransaction.TRANSIT_FRAGMENT_OPEN)
                .replace(R.id.container, fragment)
                .addToBackStack(null)
                .commit();
    }

    private void rebuildItemList() {
        mProfileManager.reloadProfiles();
        mItems.clear();
        for(Profile profile : mProfileManager.getProfiles()) {
            mItems.add(new ProfileItem(profile));
        }
        mAdapter.notifyDataSetChanged();
    }

    @Override
    public Animator onCreateAnimator(int transit, boolean enter, int nextAnim) {
        ViewGroup vg = (ViewGroup) getActivity().findViewById(android.R.id.content);
        int h = vg.getHeight();
        int w = vg.getWidth();
        AnimatorSet as = new AnimatorSet();
        if (transit == FragmentTransaction.TRANSIT_FRAGMENT_OPEN) {
            if (enter) {
                return ObjectAnimator.ofFloat(getView(), "x", w, 0.0f).setDuration(200);
            } else {
                as.playTogether(ObjectAnimator.ofFloat(getView(), View.ALPHA, 1, 0).setDuration(200),
                        ObjectAnimator.ofFloat(getView(), "y", 0.0f, -h).setDuration(200));
                return ObjectAnimator.ofFloat(getView(), View.ALPHA, 1, 0).setDuration(200);
            }
        } else if (transit == FragmentTransaction.TRANSIT_FRAGMENT_CLOSE) {
            if (enter) {
                as.playTogether(ObjectAnimator.ofFloat(getView(), View.ALPHA, 0, 1).setDuration(200),
                        ObjectAnimator.ofFloat(getView(), "y", -h, 0.0f).setDuration(200));
                return ObjectAnimator.ofFloat(getView(), View.ALPHA, 0, 1).setDuration(200);
            } else {
                return ObjectAnimator.ofFloat(getView(), "x", 0.0f, w).setDuration(200);
            }
        }
        return super.onCreateAnimator(transit, enter, nextAnim);
    }
}
