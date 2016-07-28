package xtuaok.sharegyazo.item;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import xtuaok.sharegyazo.Profile;
import xtuaok.sharegyazo.R;

/**
 * Created by takuo on 7/22/16.
 */
public class GyazoIDItem implements Item {
    String mTitle;
    Profile mProfile;

    public GyazoIDItem(String title, Profile profile) {
        this.mTitle = title;
        this.mProfile = profile;
    }

    @Override
    public ItemListAdapter.RowType getRowType() {
        return ItemListAdapter.RowType.TEXT_ITEM;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }

    @Override
    public View getView(LayoutInflater inflater, View convertView, ViewGroup parent) {
        View view;
        if (convertView == null) {
            view = inflater.inflate(R.layout.list_two_line_item, parent, false);
            // Do some initialization
        } else {
            view = convertView;
        }

        TextView text = (TextView) view.findViewById(R.id.title);
        text.setText(mTitle);

        text = (TextView) view.findViewById(R.id.summary);
        text.setText(mProfile.getGyazoID());

        return view;
    }
}
