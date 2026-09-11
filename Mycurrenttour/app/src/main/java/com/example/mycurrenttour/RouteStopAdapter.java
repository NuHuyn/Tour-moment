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

import java.util.List;
import java.util.Locale;

/**
 * Horizontally-scrolling stop photo cards on Route Details (OngoingMapActivity), one per
 * tour.getWaypoints() entry - see item_route_stop_card.xml. Separate from WaypointViewAdapter
 * (unused elsewhere now - see its own header comment) since the two screens no longer share a
 * layout.
 *
 * Lock state is read straight off each Tour.Waypoint.isLocked() - set by the server per-device on
 * every fetch (see tour-backend's waypointVisibility.js) - rather than a Set the activity has to
 * keep in sync separately. A locked waypoint's locationName/note/photos are already redacted by
 * the server (masked name, generic teaser, no photo URL), so this adapter's only job for a locked
 * card is to render that redacted data with a tasteful "mystery location" look: a blurred generic
 * placeholder photo + soft scrim (see BlurTransformation / viewStopLockedScrim) instead of the
 * real photo, which was never sent in the first place.
 */
public class RouteStopAdapter extends RecyclerView.Adapter<RouteStopAdapter.ViewHolder> {

    public interface OnStopClickListener {
        /** Tapped an already-unlocked card - draw an on-demand route from the user's current
         *  location to this one waypoint (Feature 1). */
        void onStopClick(int position);
        /** Tapped a still-locked card - open the per-step unlock dialog. */
        void onLockClick(int position);
    }

    private List<Tour.Waypoint> waypoints;
    private final OnStopClickListener listener;
    /** Last unlocked stop - drawn with the green "currently active" border. */
    private int activePosition = 0;

    public RouteStopAdapter(List<Tour.Waypoint> waypoints, OnStopClickListener listener) {
        this.waypoints = waypoints;
        this.listener = listener;
        this.activePosition = computeActivePosition();
    }

    /** Swaps in a fresh waypoints list (e.g. after refetching the tour post-unlock, where a
     *  previously-redacted waypoint's real data has just become visible) and rebinds everything. */
    public void updateWaypoints(List<Tour.Waypoint> waypoints) {
        this.waypoints = waypoints;
        this.activePosition = computeActivePosition();
        notifyDataSetChanged();
    }

    /** Highest-index stop that's already unlocked - falls back to the last stop if none are locked. */
    private int computeActivePosition() {
        int count = getItemCount();
        int active = 0;
        for (int i = 0; i < count; i++) {
            if (!waypoints.get(i).isLocked()) active = i;
        }
        return active;
    }

    public int getActivePosition() {
        return activePosition;
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        MaterialCardView card;
        ImageView imgPhoto, imgStatus;
        View lockedScrim;
        TextView txtNumber, txtName, txtDesc;

        ViewHolder(View itemView) {
            super(itemView);
            card = (MaterialCardView) itemView;
            imgPhoto = itemView.findViewById(R.id.imgStopPhoto);
            imgStatus = itemView.findViewById(R.id.imgStopStatus);
            lockedScrim = itemView.findViewById(R.id.viewStopLockedScrim);
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
        boolean locked = wp.isLocked();
        android.content.Context ctx = holder.itemView.getContext();

        holder.txtNumber.setText(String.format(Locale.getDefault(), "%02d", position + 1));
        // Server already masks locationName/note for a locked waypoint (e.g. "🔒 Điểm chưa mở
        // khóa - Buôn Đôn") - this just renders whatever came back, real or redacted.
        holder.txtName.setText(wp.getLocationName() != null ? wp.getLocationName() : "Stop " + (position + 1));

        String desc = wp.getNote();
        holder.txtDesc.setVisibility(desc != null && !desc.trim().isEmpty() ? View.VISIBLE : View.GONE);
        if (desc != null) holder.txtDesc.setText(desc);

        holder.lockedScrim.setVisibility(locked ? View.VISIBLE : View.GONE);

        if (locked) {
            // Real photo was never sent for a locked waypoint - always the blurred generic
            // placeholder, never the actual destination photo (there isn't one to blur).
            Picasso.get().load(R.drawable.centralvietnam)
                    .transform(new BlurTransformation())
                    .into(holder.imgPhoto);
        } else if (wp.getPhotos() != null && !wp.getPhotos().isEmpty() && !wp.getPhotos().get(0).isEmpty()) {
            // Also guard the first photo string itself, not just the list - Picasso.load("")
            // throws ("Path must not be empty"), and a non-empty list could still contain "".
            Picasso.get().load(wp.getPhotos().get(0))
                    .placeholder(R.drawable.centralvietnam)
                    .error(R.drawable.centralvietnam)
                    .into(holder.imgPhoto);
        } else {
            holder.imgPhoto.setImageResource(R.drawable.centralvietnam);
        }

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
