package com.example.mycurrenttour;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
// Thay Picasso bằng Glide
import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions;
import com.bumptech.glide.request.RequestOptions;

import java.util.List;

public class SlideshowAdapter extends RecyclerView.Adapter<SlideshowAdapter.ViewHolder> {
    private List<String> imageUrls;

    public SlideshowAdapter(List<String> imageUrls) {
        this.imageUrls = imageUrls;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        // Đảm bảo item_slide_image.xml sử dụng match_parent cho cả 2 chiều
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_slide_image, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        // Sử dụng Glide để tối ưu bộ nhớ và hiệu ứng mờ dần (CrossFade)
        Glide.with(holder.itemView.getContext())
                .load(imageUrls.get(position))
                .apply(new RequestOptions()
                        .centerCrop()
                        .dontAnimate() // Quan trọng: Bỏ hiệu ứng mặc định của Glide để dùng Transformer của mình
                        .diskCacheStrategy(DiskCacheStrategy.ALL)) // Lưu vào bộ nhớ máy để lần sau không phải load lại
                .into(holder.imageView);
    }

    @Override
    public int getItemCount() { return imageUrls != null ? imageUrls.size() : 0; }

    class ViewHolder extends RecyclerView.ViewHolder {
        ImageView imageView;
        ViewHolder(View itemView) {
            super(itemView);
            imageView = itemView.findViewById(R.id.imgSlide);
        }
    }
}