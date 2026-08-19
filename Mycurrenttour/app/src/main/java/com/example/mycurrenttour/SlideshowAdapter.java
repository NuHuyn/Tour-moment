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
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_slide_image, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        // Bug fix: no placeholder/error fallback meant a null or unreachable URL (e.g. Tour
        // Detail's hero carousel falling back to a single null "photo" when a tour has no images)
        // rendered as a solid black slide instead of any image at all.
        Glide.with(holder.itemView.getContext())
                .load(imageUrls.get(position))
                .apply(new RequestOptions()
                        .centerCrop()
                        .dontAnimate()
                        .placeholder(R.drawable.centralvietnam)
                        .error(R.drawable.centralvietnam)
                        .diskCacheStrategy(DiskCacheStrategy.ALL))
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