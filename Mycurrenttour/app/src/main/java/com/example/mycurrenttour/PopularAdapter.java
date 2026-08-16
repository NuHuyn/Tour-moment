package com.example.mycurrenttour;

import android.content.Context;
import android.content.Intent;
import android.util.DisplayMetrics;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.squareup.picasso.Picasso;

import java.util.List;

public class PopularAdapter extends RecyclerView.Adapter<PopularAdapter.ViewHolder> {

    private Context context;
    private List<Tour> list;

    // Card size in px, computed once from the screen width (not per-row) - see computeCardSize().
    private int cardWidthPx = -1;
    private int cardHeightPx = -1;

    public PopularAdapter(Context context, List<Tour> list){
        this.context = context;
        this.list = list;
    }

    /** Sizes the card off the actual screen width so exactly 3 land fully in the viewport (was a
     *  hardcoded 190dp, wide enough that the 3rd card got cut off). Mirrors
     *  fragment_discovery.xml's content column (32dp total side padding) and this item's 12dp
     *  layout_marginEnd gap between cards, then keeps the original 190:230 (~1:1.21) portrait
     *  ratio the design used at full size. */
    private void computeCardSize() {
        if (cardWidthPx > 0) return;
        DisplayMetrics dm = context.getResources().getDisplayMetrics();
        int sideInsetPx = (int) (32 * dm.density);
        int gapPx = (int) (12 * dm.density);
        cardWidthPx = (dm.widthPixels - sideInsetPx - gapPx * 2) / 3;
        cardHeightPx = (int) (cardWidthPx * 230f / 190f);
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_popular, parent, false);

        computeCardSize();
        ViewHolder holder = new ViewHolder(view);

        ViewGroup.LayoutParams cardLp = view.getLayoutParams();
        cardLp.width = cardWidthPx;
        view.setLayoutParams(cardLp);

        ViewGroup.LayoutParams imgLp = holder.img.getLayoutParams();
        imgLp.height = cardHeightPx;
        holder.img.setLayoutParams(imgLp);

        return holder;
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Tour tour = list.get(position);

        // Landmark + province, from the tour's first waypoint locationName (e.g.
        // "Bản Cát Cát - Sa Pa") - the same free-text field the chip filter already
        // substring-matches against; Tour/Waypoint has no separate structured province field.
        String locationName = (tour.getWaypoints() != null && !tour.getWaypoints().isEmpty())
                ? tour.getWaypoints().get(0).getLocationName() : null;

        String landmark = null, province = null;
        if (locationName != null && !locationName.trim().isEmpty()) {
            int dashIndex = locationName.lastIndexOf(" - ");
            if (dashIndex >= 0) {
                landmark = locationName.substring(0, dashIndex).trim();
                province = locationName.substring(dashIndex + 3).trim();
            } else {
                landmark = locationName.trim();
            }
        }
        if (landmark == null || landmark.isEmpty()) {
            landmark = tour.getTitle() != null ? tour.getTitle() : "Popular Tour";
        }
        holder.txtName.setText(landmark);
        if (province != null && !province.isEmpty()) {
            holder.txtProvince.setText(province);
            holder.txtProvince.setVisibility(View.VISIBLE);
        } else {
            holder.txtProvince.setVisibility(View.GONE);
        }

        String imageUrl = tour.getImageUrl();

        Picasso.get()
                .load(imageUrl)
                .fit()
                .centerCrop()
                .placeholder(R.drawable.centralvietnam)
                .error(R.drawable.centralvietnam)
                .into(holder.img);

        holder.itemView.setOnClickListener(v -> {
            Intent intent = new Intent(context, TourDetailActivity.class);
            intent.putExtra("tour_item", tour);
            context.startActivity(intent);
        });
    }

    @Override
    public int getItemCount() {
        return list != null ? list.size() : 0;
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView img;
        TextView txtName, txtProvince;

        public ViewHolder(View itemView){
            super(itemView);
            img = itemView.findViewById(R.id.imgPopular);
            txtName = itemView.findViewById(R.id.txtPopularName);
            txtProvince = itemView.findViewById(R.id.txtPopularProvince);
        }
    }
}
