package com.jox3.tv.ui.home;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.jox3.tv.R;
import com.jox3.tv.model.MediaItem;

import java.util.List;

/**
 * Tira compacta de miniaturas para cambiar de canal sin salir de Home
 * (panel horizontal, bajo el reproductor embebido). Muestra el logo del
 * canal + su nombre debajo, y un anillo de acento en la miniatura activa.
 */
public class MiniChannelStripAdapter extends RecyclerView.Adapter<MiniChannelStripAdapter.ThumbHolder> {

    public interface OnChannelPick {
        void onPick(MediaItem channel);
    }

    private final List<MediaItem> channels;
    private final OnChannelPick listener;
    private String activeChannelId;

    public MiniChannelStripAdapter(List<MediaItem> channels, OnChannelPick listener) {
        this.channels = channels;
        this.listener = listener;
    }

    /** Se llama cada vez que cambia el canal activo del reproductor, para mover el anillo de acento. */
    public void setActiveChannelId(String channelId) {
        this.activeChannelId = channelId;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ThumbHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_mini_channel_thumb, parent, false);
        return new ThumbHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ThumbHolder holder, int position) {
        MediaItem channel = channels.get(position);

        if (channel.logoUrl != null && !channel.logoUrl.isEmpty()) {
            Glide.with(holder.img.getContext())
                    .load(channel.logoUrl)
                    .placeholder(android.R.color.transparent)
                    .error(android.R.color.transparent)
                    .into(holder.img);
        } else {
            holder.img.setImageDrawable(null);
        }

        holder.name.setText(channel.name);

        boolean isActive = channel.id.equals(activeChannelId);
        holder.activeRing.setVisibility(isActive ? View.VISIBLE : View.GONE);
        holder.name.setTextColor(ContextCompat.getColor(holder.itemView.getContext(),
                isActive ? R.color.accent : R.color.text_secondary));

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onPick(channel);
        });
    }

    @Override
    public int getItemCount() {
        return channels.size();
    }

    static class ThumbHolder extends RecyclerView.ViewHolder {
        ImageView img;
        TextView name;
        View activeRing;

        ThumbHolder(@NonNull View itemView) {
            super(itemView);
            img = itemView.findViewById(R.id.strip_thumb_img);
            name = itemView.findViewById(R.id.strip_thumb_name);
            activeRing = itemView.findViewById(R.id.strip_thumb_active_ring);
        }
    }
}
