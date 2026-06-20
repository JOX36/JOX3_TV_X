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

public class HeroSlideAdapter extends RecyclerView.Adapter<HeroSlideAdapter.SlideHolder> {

    public interface OnHeroAction {
        void onPlay(MediaItem item);
        void onToggleFav(MediaItem item);
    }

    private final List<MediaItem> items;
    private final AppPrefs prefs;
    private final OnHeroAction listener;

    public HeroSlideAdapter(List<MediaItem> items, AppPrefs prefs, OnHeroAction listener) {
        this.items = items;
        this.prefs = prefs;
        this.listener = listener;
    }

    @NonNull
    @Override
    public SlideHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_hero_slide, parent, false);
        return new SlideHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull SlideHolder holder, int position) {
        MediaItem item = items.get(position);

        holder.title.setText(item.name);
        holder.badge.setVisibility(MediaItem.LIVE.equals(item.type) ? View.VISIBLE : View.GONE);
        holder.category.setText(item.category != null ? item.category.toUpperCase() : "");

        if (item.synopsis != null && !item.synopsis.isEmpty()) {
            holder.synopsis.setText(item.synopsis);
            holder.synopsis.setVisibility(View.VISIBLE);
        } else {
            holder.synopsis.setText("Disponible en tu lista privada.");
            holder.synopsis.setVisibility(View.VISIBLE);
        }

        if (item.logoUrl != null && !item.logoUrl.isEmpty()) {
            Glide.with(holder.poster.getContext())
                    .load(item.logoUrl)
                    .centerCrop()
                    .into(holder.poster);
        } else {
            holder.poster.setImageDrawable(null);
        }

        updateFavText(holder, item);

        holder.btnPlay.setOnClickListener(v -> {
            if (listener != null) listener.onPlay(item);
        });
        holder.btnFav.setOnClickListener(v -> {
            if (listener != null) listener.onToggleFav(item);
            updateFavText(holder, item);
        });
    }

    public void notifySynopsisLoaded(int position) {
        if (position >= 0 && position < items.size()) {
            notifyItemChanged(position);
        }
    }

    private void updateFavText(SlideHolder holder, MediaItem item) {
        boolean isFav = prefs != null && prefs.isFav(item.favKey());
        holder.btnFav.setText(isFav ? "★ En favoritos" : "☆ Favorito");
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class SlideHolder extends RecyclerView.ViewHolder {
        ImageView poster;
        TextView title, category, synopsis, badge, btnPlay, btnFav;

        SlideHolder(@NonNull View itemView) {
            super(itemView);
            poster = itemView.findViewById(R.id.hero_poster);
            title = itemView.findViewById(R.id.hero_title);
            category = itemView.findViewById(R.id.hero_category);
            synopsis = itemView.findViewById(R.id.hero_synopsis);
            badge = itemView.findViewById(R.id.hero_badge);
            btnPlay = itemView.findViewById(R.id.hero_btn_play);
            btnFav = itemView.findViewById(R.id.hero_btn_fav);
        }
    }
}
