package xtuaok.sharegyazo.item;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

public interface Item {
    ItemListAdapter.RowType getRowType();
    View getView(LayoutInflater inflater, View convertView, ViewGroup parent);
    boolean isEnabled();
}
