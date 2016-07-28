package xtuaok.sharegyazo.item;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import xtuaok.sharegyazo.R;
import xtuaok.sharegyazo.Profile;

public class ImageFormatItem implements Item {
    Profile mProfile;
    String mTitle;

    public ImageFormatItem(String title, Profile profile) {
        this.mTitle = title;
        this.mProfile = profile;
    }

    @Override
    public ItemListAdapter.RowType getRowType() {
        return ItemListAdapter.RowType.FORMAT_ITEM;
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
        text.setText(getImageFormatName(mProfile.getFormat()));

        return view;
    }

    private String getImageFormatName(String format) {
        switch (format) {
            case "png": return "PNG";
            case "jpg": return "JPEG";
            case "gif": return "GIF";
            default: return "PNG";
        }
    }
}
