package com.example.mycurrenttour;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.squareup.picasso.Picasso;
import java.util.List;

public class WaypointViewAdapter extends RecyclerView.Adapter<WaypointViewAdapter.ViewHolder> {

    private List<Tour.Waypoint> waypointList;
    private OnWaypointClickListener listener;
    private boolean isOngoing;

    public interface OnWaypointClickListener {
        void onWaypointClick(int position);
    }

    public WaypointViewAdapter(List<Tour.Waypoint> waypointList, boolean isOngoing, OnWaypointClickListener listener) {
        this.waypointList = waypointList;
        this.isOngoing = isOngoing;
        this.listener = listener;
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        TextView txtWaypointInfo, txtWaypointCost, txtWaypointNote;
        ImageButton btnZoom;
        ImageView imgWaypointDetail, imgExpandArrow;
        LinearLayout layoutHeader, layoutDetailContainer;

        public ViewHolder(View itemView) {
            super(itemView);
            layoutHeader = itemView.findViewById(R.id.layoutHeader);
            txtWaypointInfo = itemView.findViewById(R.id.txtWaypointInfo);
            btnZoom = itemView.findViewById(R.id.btnZoomStep);
            imgExpandArrow = itemView.findViewById(R.id.imgExpandArrow);
            layoutDetailContainer = itemView.findViewById(R.id.layoutDetailContainer);
            imgWaypointDetail = itemView.findViewById(R.id.imgWaypointDetail);
            txtWaypointCost = itemView.findViewById(R.id.txtWaypointCost);
            txtWaypointNote = itemView.findViewById(R.id.txtWaypointNote);
        }
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_waypoint_view, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Tour.Waypoint currentWp = waypointList.get(position);

        if (isOngoing) {
            String startName = (position == 0) ? "My Location" : waypointList.get(position - 1).getLocationName();
            holder.txtWaypointInfo.setText("Step " + (position + 1) + ": " + startName + " ➔ " + currentWp.getLocationName());
        } else {
            if (position < waypointList.size() - 1) {
                Tour.Waypoint nextWp = waypointList.get(position + 1);
                holder.txtWaypointInfo.setText("Step " + (position + 1) + ": " + currentWp.getLocationName() + " ➔ " + nextWp.getLocationName());
            } else {
                holder.txtWaypointInfo.setText("Destination: " + currentWp.getLocationName());
            }
        }

        boolean expanded = currentWp.isExpanded();
        holder.layoutDetailContainer.setVisibility(expanded ? View.VISIBLE : View.GONE);
        holder.imgExpandArrow.setRotation(expanded ? 180 : 0);

        holder.txtWaypointCost.setText("Cost: $" + currentWp.getPrice());
        holder.txtWaypointNote.setText("Note: " +
                (currentWp.getNote() != null && !currentWp.getNote().isEmpty() ? currentWp.getNote() : "No notes available"));

        if (currentWp.getPhotos() != null && !currentWp.getPhotos().isEmpty()) {
            Picasso.get().load(currentWp.getPhotos().get(0))
                    .placeholder(R.drawable.centralvietnam)
                    .error(R.drawable.centralvietnam)
                    .into(holder.imgWaypointDetail);
        } else {
            holder.imgWaypointDetail.setImageResource(R.drawable.centralvietnam);
        }

        holder.layoutHeader.setOnClickListener(v -> {
            currentWp.setExpanded(!currentWp.isExpanded());
            notifyItemChanged(position);
        });

        holder.btnZoom.setOnClickListener(v -> {
            if (listener != null) {
                listener.onWaypointClick(position);
            }
        });
    }

    @Override
    public int getItemCount() {
        return waypointList != null ? waypointList.size() : 0;
    }
}
