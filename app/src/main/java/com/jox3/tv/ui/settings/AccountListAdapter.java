package com.jox3.tv.ui.settings;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.jox3.tv.R;
import com.jox3.tv.model.PlaylistConfig;

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
        holder.activeDot.setVisibility(isActive ? View.VISIBLE : View.INVISIBLE);

        StringBuilder meta = new StringBuilder();
        meta.append(PlaylistConfig.TYPE_XTREAM.equals(account.type) ? "Xtream Codes" : "Lista M3U");
        if (isActive) meta.append(" · Activa");
        if (account.expDateEpoch > 0) {
            long daysLeft = (account.expDateEpoch * 1000L - System.currentTimeMillis()) / (1000L * 60 * 60 * 24);
            meta.append(" · Vence ").append(formatDate(account.expDateEpoch));
            if (daysLeft >= 0) meta.append(" (").append(daysLeft).append("d)");
        }
        holder.meta.setText(meta.toString());

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onSelect(account);
        });
        holder.deleteText.setOnClickListener(v -> {
            if (listener != null) listener.onDelete(account);
        });
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
        TextView name, meta, deleteText;
        View activeDot;

        AccountHolder(@NonNull View itemView) {
            super(itemView);
            name = itemView.findViewById(R.id.account_name);
            meta = itemView.findViewById(R.id.account_meta);
            deleteText = itemView.findViewById(R.id.account_delete_text);
            activeDot = itemView.findViewById(R.id.account_active_dot);
        }
    }
}
