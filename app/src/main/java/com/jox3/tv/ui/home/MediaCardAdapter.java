package com.jox3.tv.ui.home;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.jox3.tv.R;
import com.jox3.tv.model.MediaItem;
import com.jox3.tv.util.AppPrefs;

import java.util.List;

public class MediaCardAdapter extends RecyclerView.Adapter<MediaCardAdapter.CardHolder> {

    public interface OnItemClick {
        void onClick(MediaItem item, int position);
    }

    private final List<MediaItem> items;
    private final AppPrefs prefs;
    private final OnItemClick listener;

    public MediaCardAdapter(List<MediaItem> items, AppPrefs prefs, OnItemClick listener) {
        this.items = items;
        this.prefs = prefs;
        this.listener = listener;
    }

    @NonNull
    @Override
    public CardHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_media_card, parent, false);
        return new CardHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull CardHolder holder, int position) {
        MediaItem item = items.get(position);

        holder.tvName.setText(item.name);
        holder.tvCategory.setText(item.category != null ? item.category : "");

        holder.tvLiveBadge.setVisibility(
                MediaItem.LIVE.equals(item.type) ? View.VISIBLE : View.GONE);

        boolean isFav = prefs != null && prefs.isFav(item.favKey());
        holder.tvFavBadge.setVisibility(isFav ? View.VISIBLE : View.GONE);

        if (item.logoUrl != null && !item.logoUrl.isEmpty()) {
            Glide.with(holder.imgLogo.getContext())
                    .load(item.logoUrl)
                    .placeholder(android.R.color.transparent)
                    .error(android.R.color.transparent)
                    .into(holder.imgLogo);
        } else {
            holder.imgLogo.setImageDrawable(null);
        }

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onClick(item, position);
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    public void updateData(List<MediaItem> newItems) {
        items.clear();
        items.addAll(newItems);
        notifyDataSetChanged();
    }

    static class CardHolder extends RecyclerView.ViewHolder {
        ImageView imgLogo;
        TextView tvName, tvCategory, tvLiveBadge, tvFavBadge;

        CardHolder(@NonNull View itemView) {
            super(itemView);
            imgLogo = itemView.findViewById(R.id.img_logo);
            tvName = itemView.findViewById(R.id.tv_name);
            tvCategory = itemView.findViewById(R.id.tv_category);
            tvLiveBadge = itemView.findViewById(R.id.tv_live_badge);
            tvFavBadge = itemView.findViewById(R.id.tv_fav_badge);
        }
    }
}
