package com.example.mycurrenttour;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.card.MaterialCardView;
import com.squareup.picasso.Picasso;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Horizontally-scrolling stop photo cards on Route Details (OngoingMapActivity), one per
 * tour.getWaypoints() entry - see item_route_stop_card.xml. Separate from WaypointViewAdapter
 * (the accordion-style waypoint list still used elsewhere, e.g. TourDetailActivity) since the two
 * screens no longer share a layout.
 */
public class RouteStopAdapter extends RecyclerView.Adapter<RouteStopAdapter.ViewHolder> {

    public interface OnStopClickListener {
        /** Tapped an already-unlocked card - e.g. recenter the map / draw the route to this stop. */
        void onStopClick(int position);
        /** Tapped a still-locked card's status badge - open the per-step unlock dialog. */
        void onLockClick(int position);
    }

    private final List<Tour.Waypoint> waypoints;
    private final OnStopClickListener listener;
    private Set<Integer> lockedPositions = new HashSet<>();
    /** Last unlocked stop - the one drawn with the green "currently active" border. */
    private int activePosition = 0;

    public RouteStopAdapter(List<Tour.Waypoint> waypoints, OnStopClickListener listener) {
        this.waypoints = waypoints;
        this.listener = listener;
    }

    public void setLockedPositions(Set<Integer> lockedPositions) {
        this.lockedPositions = lockedPositions != null ? lockedPositions : Collections.emptySet();
        activePosition = computeActivePosition();
        notifyDataSetChanged();
    }

    /** Highest-index stop that's already unlocked - falls back to the last stop if none are locked. */
    private int computeActivePosition() {
        int count = getItemCount();
        int active = 0;
        for (int i = 0; i < count; i++) {
            if (!lockedPositions.contains(i)) active = i;
        }
        return active;
    }

    public int getActivePosition() {
        return activePosition;
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        MaterialCardView card;
        ImageView imgPhoto, imgStatus;
        TextView txtNumber, txtName, txtDesc;

        ViewHolder(View itemView) {
            super(itemView);
            card = (MaterialCardView) itemView;
            imgPhoto = itemView.findViewById(R.id.imgStopPhoto);
            imgStatus = itemView.findViewById(R.id.imgStopStatus);
            txtNumber = itemView.findViewById(R.id.txtStopNumber);
            txtName = itemView.findViewById(R.id.txtStopName);
            txtDesc = itemView.findViewById(R.id.txtStopDesc);
        }
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_route_stop_card, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Tour.Waypoint wp = waypoints.get(position);
        android.content.Context ctx = holder.itemView.getContext();

        holder.txtNumber.setText(String.format(Locale.getDefault(), "%02d", position + 1));
        holder.txtName.setText(wp.getLocationName() != null ? wp.getLocationName() : "Stop " + (position + 1));

        String desc = wp.getNote();
        holder.txtDesc.setVisibility(desc != null && !desc.trim().isEmpty() ? View.VISIBLE : View.GONE);
        if (desc != null) holder.txtDesc.setText(desc);

        // Also guard the first photo string itself, not just the list - Picasso.load("") throws
        // ("Path must not be empty"), and a non-empty list could still contain an empty string.
        if (wp.getPhotos() != null && !wp.getPhotos().isEmpty() && !wp.getPhotos().get(0).isEmpty()) {
            Picasso.get().load(wp.getPhotos().get(0))
                    .placeholder(R.drawable.centralvietnam)
                    .error(R.drawable.centralvietnam)
                    .into(holder.imgPhoto);
        } else {
            holder.imgPhoto.setImageResource(R.drawable.centralvietnam);
        }

        boolean locked = lockedPositions.contains(position);
        if (locked) {
            holder.imgStatus.setBackgroundResource(R.drawable.bg_circle_gray_status);
            holder.imgStatus.setImageResource(R.drawable.ic_lock);
        } else {
            holder.imgStatus.setBackgroundResource(R.drawable.bg_circle_green);
            holder.imgStatus.setImageResource(R.drawable.ic_check_plain);
        }

        boolean isActive = position == activePosition;
        holder.card.setStrokeWidth(isActive ? dp(ctx, 2) : 0);

        holder.itemView.setOnClickListener(v -> {
            if (listener == null) return;
            if (locked) listener.onLockClick(position);
            else listener.onStopClick(position);
        });
    }

    private int dp(android.content.Context ctx, int value) {
        return Math.round(value * ctx.getResources().getDisplayMetrics().density);
    }

    @Override
    public int getItemCount() {
        return waypoints != null ? waypoints.size() : 0;
    }
}
