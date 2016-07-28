package xtuaok.sharegyazo.item;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import xtuaok.sharegyazo.R;

/**
 * Created by takuo on 7/22/16.
 */
public class Header implements Item {
        private final String name;

    public Header(String name) {
        this.name = name;
    }

    @Override
    public ItemListAdapter.RowType getRowType() {
        return ItemListAdapter.RowType.HEADER_ITEM;
    }

    @Override
    public View getView(LayoutInflater inflater, View convertView, ViewGroup parent) {
        View view;
        if (convertView == null) {
            view = inflater.inflate(R.layout.profile_list_header, parent, false);
            // Do some initialization
        } else {
            view = convertView;
        }

        TextView text = (TextView) view.findViewById(R.id.title);
        text.setText(name);

        return view;
    }

    @Override
    public boolean isEnabled() {
        return false;
    }

}
