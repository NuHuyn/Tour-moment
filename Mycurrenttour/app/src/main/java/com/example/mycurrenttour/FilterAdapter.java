
package com.example.mycurrenttour;

import android.content.Context;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.TextView;
import com.example.mycurrenttour.R;

import java.util.List;
public class FilterAdapter extends BaseAdapter {

    Context context;
    List<String> list;
    int selectedPosition = -1;

    public FilterAdapter(Context context, List<String> list) {
        this.context = context;
        this.list = list;
    }

    public void setSelected(int position) {
        selectedPosition = position;
        notifyDataSetChanged();
    }

    @Override
    public int getCount() { return list.size(); }

    @Override
    public Object getItem(int i) { return list.get(i); }

    @Override
    public long getItemId(int i) { return i; }

    @Override
    public View getView(int i, View view, ViewGroup parent) {
        if (view == null) {
            view = LayoutInflater.from(context).inflate(R.layout.item_filter, parent, false);
        }

        TextView txt = view.findViewById(R.id.txtName);
        ImageView tick = view.findViewById(R.id.imgTick);

        txt.setText(list.get(i));

        if (i == selectedPosition) {
            view.setBackgroundColor(Color.parseColor("#A5D6A7"));
            tick.setVisibility(View.VISIBLE);
        } else {
            view.setBackgroundColor(Color.TRANSPARENT);
            tick.setVisibility(View.GONE);
        }

        return view;
    }
}