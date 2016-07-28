package xtuaok.sharegyazo.item;

/**
 * Created by takuo on 7/22/16.
 */
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;

import java.util.List;

public class ItemListAdapter extends ArrayAdapter<Item> {
    private LayoutInflater mInflater;
    public enum RowType {
        HEADER_ITEM,
        NAME_ITEM,
        TEXT_ITEM,
        FORMAT_ITEM,
        QUALITY_ITEM,
        PROFILE_ITEM
    }

    public ItemListAdapter(Context context, List<Item> items) {
        super(context, 0, items);
        mInflater = LayoutInflater.from(context);
    }

    @Override
    public int getViewTypeCount() {
        return RowType.values().length;
    }

    @Override
    public int getItemViewType(int position) {
        return getItem(position).getRowType().ordinal();
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        return getItem(position).getView(mInflater, convertView, parent);
    }

    @Override
    public boolean areAllItemsEnabled() {
        return false;
    }

    @Override
    public boolean isEnabled(int position) {
        return getItem(position).isEnabled();
    }

}
