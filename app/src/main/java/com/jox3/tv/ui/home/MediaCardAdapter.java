package com.jox3.tv.ui.home;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
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
    private final int layoutResId;

    public MediaCardAdapter(List<MediaItem> items, AppPrefs prefs, OnItemClick listener) {
        this(items, prefs, listener, R.layout.item_media_card);
    }

    public MediaCardAdapter(List<MediaItem> items, AppPrefs prefs, OnItemClick listener, int layoutResId) {
        this.items = items;
        this.prefs = prefs;
        this.listener = listener;
        this.layoutResId = layoutResId;
    }

    @NonNull
    @Override
    public CardHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(layoutResId, parent, false);
        return new CardHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull CardHolder holder, int position) {
        MediaItem item = items.get(position);

        holder.tvName.setText(item.name);
        holder.tvCategory.setText(item.category != null ? item.category : "");

        holder.tvLiveBadge.setVisibility(
                MediaItem.LIVE.equals(item.type) ? View.VISIBLE : View.GONE);

        bindQualityBadge(holder, item);
        bindEpisodeBadge(holder, item);

        boolean isFav = prefs != null && prefs.isFav(item.favKey());
        holder.tvFavBadge.setVisibility(isFav ? View.VISIBLE : View.GONE);

        bindProgress(holder, item);

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

    /**
     * MediaItem no trae un campo de "calidad" separado: en las listas
     * IPTV ese dato casi siempre va metido en el propio nombre del canal
     * o título (ej. "ESPN HD", "WIN SPORT+ FHD", "HBO Max 4K"). Esto lo
     * detecta buscando esas palabras en el nombre, sin inventar nada — si
     * el nombre no la menciona, no se muestra ningún badge.
     */
    private void bindQualityBadge(CardHolder holder, MediaItem item) {
        if (holder.tvQualityBadge == null) return;
        String quality = detectQuality(item.name);
        if (quality != null) {
            holder.tvQualityBadge.setText(quality);
            holder.tvQualityBadge.setVisibility(View.VISIBLE);
        } else {
            holder.tvQualityBadge.setVisibility(View.GONE);
        }
    }

    /** Para episodios de serie con temporada/episodio conocidos, ej. "T1 E4". */
    private void bindEpisodeBadge(CardHolder holder, MediaItem item) {
        if (holder.tvEpisodeBadge == null) return;
        if (MediaItem.SERIES.equals(item.type) && item.season >= 0 && item.episode >= 0) {
            holder.tvEpisodeBadge.setText("T" + item.season + " E" + item.episode);
            holder.tvEpisodeBadge.setVisibility(View.VISIBLE);
        } else {
            holder.tvEpisodeBadge.setVisibility(View.GONE);
        }
    }

    /**
     * Público y estático para poder reutilizarlo desde otras pantallas
     * (ej. la card "Última reproducción" del Home).
     */
    public static String detectQuality(String name) {
        if (name == null) return null;
        String upper = name.toUpperCase();
        // El orden importa: hay que buscar "4K" y "FHD" ANTES que "HD",
        // porque "FHD" contiene las letras "HD" y daría un falso positivo.
        if (upper.contains("4K") || upper.contains("UHD")) return "4K";
        if (upper.contains("FHD") || upper.contains("FULL HD")) return "FHD";
        if (upper.contains("HD")) return "HD";
        if (upper.contains(" SD") || upper.endsWith("SD")) return "SD";
        return null;
    }

    /**
     * Muestra una barra de progreso en la parte inferior de la miniatura si
     * el item tiene avance guardado (VOD/series vistas parcialmente). No
     * aplica a canales en vivo, que nunca guardan progreso.
     */
    private void bindProgress(CardHolder holder, MediaItem item) {
        if (holder.progressTrack == null || holder.progressFill == null || prefs == null) return;

        if (MediaItem.LIVE.equals(item.type)) {
            holder.progressTrack.setVisibility(View.GONE);
            return;
        }

        long pos = prefs.getPos(item.id);
        long dur = prefs.getDur(item.id);
        if (dur <= 0) {
            holder.progressTrack.setVisibility(View.GONE);
            return;
        }

        int percent = (int) Math.min(100, Math.max(0, (pos * 100) / dur));
        // Solo mostramos la barra si hay avance real y no está prácticamente terminado
        if (percent < 3 || percent > 96) {
            holder.progressTrack.setVisibility(View.GONE);
            return;
        }

        holder.progressTrack.setVisibility(View.VISIBLE);
        LinearLayout.LayoutParams fillParams =
                (LinearLayout.LayoutParams) holder.progressFill.getLayoutParams();
        fillParams.weight = percent;
        holder.progressFill.setLayoutParams(fillParams);
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
        TextView tvName, tvCategory, tvLiveBadge, tvFavBadge, tvQualityBadge, tvEpisodeBadge;
        LinearLayout progressTrack;
        View progressFill;

        CardHolder(@NonNull View itemView) {
            super(itemView);
            imgLogo = itemView.findViewById(R.id.img_logo);
            tvName = itemView.findViewById(R.id.tv_name);
            tvCategory = itemView.findViewById(R.id.tv_category);
            tvLiveBadge = itemView.findViewById(R.id.tv_live_badge);
            tvFavBadge = itemView.findViewById(R.id.tv_fav_badge);
            tvQualityBadge = itemView.findViewById(R.id.tv_quality_badge);
            tvEpisodeBadge = itemView.findViewById(R.id.tv_episode_badge);
            progressTrack = itemView.findViewById(R.id.progress_track);
            progressFill = itemView.findViewById(R.id.progress_fill);
        }
    }
}
