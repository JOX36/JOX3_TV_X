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
        /** Solo se usa en la card TV (botón "Reproducir" de la propia card). */
        void onPlay(MediaItem item);
        /** Solo se usa en la card TV (botón "Favorito" de la propia card). */
        void onToggleFav(MediaItem item);
        /**
         * Se usa en la card MÓVIL: al tocar el banner completo, se abre la
         * pantalla de detalle (sinopsis, Reproducir, Favorito, Descargar)
         * en vez de reproducir directamente.
         */
        void onOpenDetail(MediaItem item);
    }

    private final List<MediaItem> items;
    private final AppPrefs prefs;
    private final OnHeroAction listener;
    private final int layoutResId;

    /** Constructor original: usa el layout móvil simple por defecto. */
    public HeroSlideAdapter(List<MediaItem> items, AppPrefs prefs, OnHeroAction listener) {
        this(items, prefs, listener, R.layout.item_hero_slide);
    }

    public HeroSlideAdapter(List<MediaItem> items, AppPrefs prefs, OnHeroAction listener, int layoutResId) {
        this.items = items;
        this.prefs = prefs;
        this.listener = listener;
        this.layoutResId = layoutResId;
    }

    @NonNull
    @Override
    public SlideHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(layoutResId, parent, false);
        return new SlideHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull SlideHolder holder, int position) {
        MediaItem item = items.get(position);

        if (holder.title != null) holder.title.setText(cleanDuplicateYear(item.name));
        if (holder.badge != null) {
            holder.badge.setVisibility(MediaItem.LIVE.equals(item.type) ? View.VISIBLE : View.GONE);
        }
        if (holder.category != null) {
            holder.category.setText(item.category != null ? item.category.toUpperCase() : "");
        }

        if (holder.synopsis != null) {
            if (item.synopsis != null && !item.synopsis.isEmpty()) {
                holder.synopsis.setText(item.synopsis);
            } else {
                holder.synopsis.setText("Disponible en tu lista privada.");
            }
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

        // Card móvil: toda la card es el botón, abre la pantalla de detalle.
        holder.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onOpenDetail(item);
        });

        // Card TV (si esos botones existen en el layout inflado): siguen
        // funcionando directo desde el banner, sin pasar por detalle.
        if (holder.btnPlay != null) {
            holder.btnPlay.setOnClickListener(v -> {
                if (listener != null) listener.onPlay(item);
            });
        }
        if (holder.btnFav != null) {
            updateFavText(holder, item);
            holder.btnFav.setOnClickListener(v -> {
                if (listener != null) listener.onToggleFav(item);
                updateFavText(holder, item);
            });
        }
    }

    /**
     * Algunas listas traen el nombre con el año repetido dos veces, ej.
     * "HUNTER (1986) (1986)" — viene así desde el origen de la lista, no
     * es algo que la app agregue. Esto lo limpia solo para mostrar, sin
     * tocar el nombre real guardado en el ítem (así Favoritos/Historial
     * siguen funcionando igual por nombre exacto).
     */
    private static String cleanDuplicateYear(String name) {
        if (name == null) return name;
        return name.replaceAll("(\\(\\d{4}\\))\\s*\\1", "$1");
    }

    /** Permite refrescar solo la sinopsis de un item ya visible, sin recargar todo. */
    public void notifySynopsisLoaded(int position) {
        if (position >= 0 && position < items.size()) {
            notifyItemChanged(position);
        }
    }

    private void updateFavText(SlideHolder holder, MediaItem item) {
        if (holder.btnFav == null) return;
        boolean isFav = prefs != null && prefs.isFav(item.favKey());
        holder.btnFav.setText(isFav ? "En favoritos" : "Favorito");
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
            badge = itemView.findViewById(R.id.hero_badge);
            // Estas 4 vistas SOLO existen en el layout TV
            // (item_hero_slide_tv.xml); en el layout móvil simple
            // (item_hero_slide.xml) findViewById devuelve null sin
            // explotar, y el resto del código ya está preparado para
            // verificar null antes de usarlas.
            category = itemView.findViewById(R.id.hero_category);
            synopsis = itemView.findViewById(R.id.hero_synopsis);
            btnPlay = itemView.findViewById(R.id.hero_btn_play);
            btnFav = itemView.findViewById(R.id.hero_btn_fav);
        }
    }
}
