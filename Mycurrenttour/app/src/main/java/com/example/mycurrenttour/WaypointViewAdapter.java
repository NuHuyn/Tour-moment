package com.example.mycurrenttour;

import android.graphics.Color;
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
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class WaypointViewAdapter extends RecyclerView.Adapter<WaypointViewAdapter.ViewHolder> {

    private static final int GREEN = Color.parseColor("#4CAF50");
    private static final float DISABLED_ALPHA = 0.35f;

    private List<Tour.Waypoint> waypointList;
    private OnWaypointClickListener listener;
    private boolean isOngoing;

    // Demo waypoint-lock feature (chưa gắn cổng thanh toán thật): vị trí các waypoint đang bị khóa.
    // Nguồn dữ liệu: WaypointLockManager, adapter chỉ vẽ theo set này, không tự tính khóa.
    private Set<Integer> lockedPositions = new HashSet<>();

    public interface OnWaypointClickListener {
        /** Bấm icon chỉ đường (🗺️) - chỉ được gọi khi step đã mở (adapter tự chặn khi đang khóa). */
        void onNavigateClick(int position);
        /** Bấm icon ổ khóa của 1 step đang khóa - mở dialog trả 2.000đ cho đúng step đó. */
        void onLockClick(int position);
    }

    public WaypointViewAdapter(List<Tour.Waypoint> waypointList, boolean isOngoing, OnWaypointClickListener listener) {
        this.waypointList = waypointList;
        this.isOngoing = isOngoing;
        this.listener = listener;
    }

    /** Cập nhật danh sách vị trí waypoint đang bị khóa và refresh UI. */
    public void setLockedPositions(Set<Integer> lockedPositions) {
        this.lockedPositions = lockedPositions != null ? lockedPositions : Collections.emptySet();
        notifyDataSetChanged();
    }

    public boolean isLocked(int position) {
        return lockedPositions.contains(position);
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        TextView txtWaypointInfo, txtWaypointCost, txtWaypointNote;
        ImageButton btnZoom, btnStatus;
        ImageView imgWaypointDetail, imgExpandArrow;
        LinearLayout layoutHeader, layoutDetailContainer;

        public ViewHolder(View itemView) {
            super(itemView);
            layoutHeader = itemView.findViewById(R.id.layoutHeader);
            txtWaypointInfo = itemView.findViewById(R.id.txtWaypointInfo);
            btnZoom = itemView.findViewById(R.id.btnZoomStep);
            btnStatus = itemView.findViewById(R.id.btnWaypointStatus);
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
            // Không còn xử lý riêng cho step cuối ("Destination") - mọi step kể cả step cuối
            // của route đều hiển thị đồng nhất "Step N: tên điểm", danh sách kết thúc tại đó.
            if (position < waypointList.size() - 1) {
                Tour.Waypoint nextWp = waypointList.get(position + 1);
                holder.txtWaypointInfo.setText("Step " + (position + 1) + ": " + currentWp.getLocationName() + " ➔ " + nextWp.getLocationName());
            } else {
                holder.txtWaypointInfo.setText("Step " + (position + 1) + ": " + currentWp.getLocationName());
            }
        }

        boolean expanded = currentWp.isExpanded();
        holder.layoutDetailContainer.setVisibility(expanded ? View.VISIBLE : View.GONE);
        holder.imgExpandArrow.setRotation(expanded ? 180 : 0);

        // Demo waypoint-lock: icon ổ khóa xanh (bấm được, mở dialog trả 2.000đ cho step này)
        // hoặc checkmark xanh (đã mở, không bấm) - đặt cạnh icon chỉ đường.
        boolean locked = isLocked(position);
        if (locked) {
            holder.btnStatus.setImageResource(R.drawable.ic_lock);
            holder.btnStatus.setColorFilter(GREEN);
            holder.btnStatus.setOnClickListener(v -> {
                if (listener != null) listener.onLockClick(position);
            });
        } else {
            holder.btnStatus.setImageResource(R.drawable.ic_check_circle);
            holder.btnStatus.setColorFilter(GREEN);
            holder.btnStatus.setOnClickListener(null);
        }

        // Chặn chỉ đường khi step đang khóa: disable + mờ đi + không phản hồi khi bấm.
        holder.btnZoom.setEnabled(!locked);
        holder.btnZoom.setAlpha(locked ? DISABLED_ALPHA : 1f);
        holder.btnZoom.setOnClickListener(locked ? null : v -> {
            if (listener != null) listener.onNavigateClick(position);
        });

        holder.txtWaypointCost.setText("Cost: $" + currentWp.getPrice());
        holder.txtWaypointNote.setText("Note: " +
                (currentWp.getNote() != null && !currentWp.getNote().isEmpty() ? currentWp.getNote() : "No notes available"));

        // Also guard the first photo string itself, not just the list - Picasso.load("") throws
        // ("Path must not be empty"), and a non-empty list could still contain an empty string.
        if (currentWp.getPhotos() != null && !currentWp.getPhotos().isEmpty() && !currentWp.getPhotos().get(0).isEmpty()) {
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
    }

    @Override
    public int getItemCount() {
        return waypointList != null ? waypointList.size() : 0;
    }
}
