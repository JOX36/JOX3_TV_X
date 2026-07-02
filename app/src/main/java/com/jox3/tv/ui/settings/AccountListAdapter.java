package com.jox3.tv.ui.settings;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.jox3.tv.R;
import com.jox3.tv.model.PlaylistConfig;
import com.jox3.tv.util.AlternateCatalogCache;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class AccountListAdapter extends RecyclerView.Adapter<AccountListAdapter.AccountHolder> {

    public interface OnAccountAction {
        void onSelect(PlaylistConfig account);
        void onDelete(PlaylistConfig account);
    }

    private final List<PlaylistConfig> accounts;
    private final String activeId;
    private final OnAccountAction listener;

    public AccountListAdapter(List<PlaylistConfig> accounts, String activeId, OnAccountAction listener) {
        this.accounts = accounts;
        this.activeId = activeId;
        this.listener = listener;
    }

    @NonNull
    @Override
    public AccountHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_account_row, parent, false);
        return new AccountHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull AccountHolder holder, int position) {
        PlaylistConfig account = accounts.get(position);
        boolean isActive = account.id.equals(activeId);

        holder.name.setText(account.name != null ? account.name : "Sin nombre");

        // Card con borde de marca para la cuenta activa; card plana para
        // el resto. Reemplaza al viejo puntito como única señal.
        holder.itemView.setBackgroundResource(
                isActive ? R.drawable.bg_card_account_active : R.drawable.bg_card_rounded);

        StringBuilder meta = new StringBuilder();
        meta.append(PlaylistConfig.TYPE_XTREAM.equals(account.type) ? "Xtream Codes" : "Lista M3U");
        if (account.expDateEpoch > 0) {
            long daysLeft = (account.expDateEpoch * 1000L - System.currentTimeMillis()) / (1000L * 60 * 60 * 24);
            meta.append(" · Vence ").append(formatDate(account.expDateEpoch));
            if (daysLeft >= 0) meta.append(" (").append(daysLeft).append("d)");
        }
        holder.meta.setText(meta.toString());

        bindStatus(holder, account, isActive);

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onSelect(account);
        });
        holder.deleteText.setOnClickListener(v -> {
            if (listener != null) listener.onDelete(account);
        });
    }

    /**
     * Pinta el punto + la etiqueta de estado de cada cuenta:
     *  - Activa ahora mismo: glow degradado de marca, sin caché relevante.
     *  - Alterna Xtream ya en caché (AlternateCatalogCache): verde, toca
     *    y cambia al instante, sin esperar al servidor.
     *  - Alterna Xtream todavía cargando en segundo plano: naranja.
     *  - Alterna Xtream cuya última carga en segundo plano falló: rojo.
     *  - Cuenta M3U: gris, esas nunca se cachean en segundo plano hoy,
     *    así que tocarla sí pide los datos en el momento.
     */
    private void bindStatus(AccountHolder holder, PlaylistConfig account, boolean isActive) {
        android.content.Context ctx = holder.itemView.getContext();

        if (isActive) {
            holder.statusDot.setImageResource(R.drawable.dot_active_glow);
            holder.statusDot.clearColorFilter();
            holder.statusLabel.setText("● Activa ahora");
            holder.statusLabel.setTextColor(ContextCompat.getColor(ctx, R.color.accent));
            return;
        }

        holder.statusDot.setImageResource(R.drawable.dot_white_solid);

        if (!PlaylistConfig.TYPE_XTREAM.equals(account.type)) {
            holder.statusDot.setColorFilter(ContextCompat.getColor(ctx, R.color.text_secondary));
            holder.statusLabel.setText("M3U · se descarga al tocar");
            holder.statusLabel.setTextColor(ContextCompat.getColor(ctx, R.color.text_secondary));
            return;
        }

        AlternateCatalogCache.AccountCatalog cache =
                AlternateCatalogCache.get().getCatalogFor(account.id);

        if (cache == null || cache.loading) {
            holder.statusDot.setColorFilter(ContextCompat.getColor(ctx, R.color.warning_orange));
            holder.statusLabel.setText("Cargando en 2do plano…");
            holder.statusLabel.setTextColor(ContextCompat.getColor(ctx, R.color.warning_orange));
        } else if (cache.loaded) {
            holder.statusDot.setColorFilter(ContextCompat.getColor(ctx, R.color.success_green));
            holder.statusLabel.setText("Lista · toca para cambio instantáneo");
            holder.statusLabel.setTextColor(ContextCompat.getColor(ctx, R.color.success_green));
        } else {
            holder.statusDot.setColorFilter(ContextCompat.getColor(ctx, R.color.live_badge));
            holder.statusLabel.setText("Sin conexión · toca para reintentar");
            holder.statusLabel.setTextColor(ContextCompat.getColor(ctx, R.color.live_badge));
        }
    }

    private String formatDate(long epochSeconds) {
        SimpleDateFormat sdf = new SimpleDateFormat("dd MMM yyyy", new Locale("es"));
        return sdf.format(new Date(epochSeconds * 1000L));
    }

    @Override
    public int getItemCount() {
        return accounts.size();
    }

    static class AccountHolder extends RecyclerView.ViewHolder {
        TextView name, meta, deleteText, statusLabel;
        ImageView statusDot;

        AccountHolder(@NonNull View itemView) {
            super(itemView);
            name = itemView.findViewById(R.id.account_name);
            meta = itemView.findViewById(R.id.account_meta);
            deleteText = itemView.findViewById(R.id.account_delete_text);
            statusDot = itemView.findViewById(R.id.account_status_dot);
            statusLabel = itemView.findViewById(R.id.account_status_label);
        }
    }
}
