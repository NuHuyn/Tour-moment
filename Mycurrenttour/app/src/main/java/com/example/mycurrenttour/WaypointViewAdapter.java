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

    public interface OnWaypointClickListener {
        void onWaypointClick(int position);
    }

    public WaypointViewAdapter(List<Tour.Waypoint> waypointList, OnWaypointClickListener listener) {
        this.waypointList = waypointList;
        this.listener = listener;
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        TextView txtWaypointInfo, txtWaypointCost, txtWaypointNote;
        ImageButton btnZoom;
        ImageView imgWaypointDetail, imgExpandArrow;
        LinearLayout layoutHeader, layoutDetailContainer;

        public ViewHolder(View itemView) {
            super(itemView);
            // Phần Header
            layoutHeader = itemView.findViewById(R.id.layoutHeader);
            txtWaypointInfo = itemView.findViewById(R.id.txtWaypointInfo);
            btnZoom = itemView.findViewById(R.id.btnZoomStep);
            imgExpandArrow = itemView.findViewById(R.id.imgExpandArrow);

            // Phần Detail (vùng xổ xuống)
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

        // 1. Hiển thị thông tin tiêu đề chặng
        if (position < waypointList.size() - 1) {
            Tour.Waypoint nextWp = waypointList.get(position + 1);
            holder.txtWaypointInfo.setText("Chặng " + (position + 1) + ": "
                    + currentWp.getLocationName() + " ➔ " + nextWp.getLocationName());
            holder.btnZoom.setVisibility(View.VISIBLE);
        } else {
            holder.txtWaypointInfo.setText("Điểm kết thúc: " + currentWp.getLocationName());
            holder.btnZoom.setVisibility(View.GONE);
        }

        // 2. Xử lý trạng thái Ẩn/Hiện vùng chi tiết dựa trên biến isExpanded
        boolean expanded = currentWp.isExpanded();
        holder.layoutDetailContainer.setVisibility(expanded ? View.VISIBLE : View.GONE);
        holder.imgExpandArrow.setRotation(expanded ? 180 : 0); // Xoay mũi tên khi mở

        // 3. Đổ dữ liệu vào phần chi tiết (Ảnh, Note, Cost)
        holder.txtWaypointCost.setText("Chi phí: $" + currentWp.getPrice());
        holder.txtWaypointNote.setText("Ghi chú: " +
                (currentWp.getNote() != null && !currentWp.getNote().isEmpty() ? currentWp.getNote() : "Không có ghi chú"));

        // Load ảnh đầu tiên nếu có
        if (currentWp.getPhotos() != null && !currentWp.getPhotos().isEmpty()) {
            Picasso.get().load(currentWp.getPhotos().get(0))
                    .placeholder(R.drawable.centralvietnam) // Ảnh chờ
                    .error(R.drawable.centralvietnam)       // Ảnh lỗi
                    .into(holder.imgWaypointDetail);
        } else {
            holder.imgWaypointDetail.setImageResource(R.drawable.centralvietnam);
        }

        // 4. Sự kiện click vào Header để Đóng/Mở
        holder.layoutHeader.setOnClickListener(v -> {
            currentWp.setExpanded(!currentWp.isExpanded());
            notifyItemChanged(position); // Cập nhật lại giao diện tại vị trí này
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