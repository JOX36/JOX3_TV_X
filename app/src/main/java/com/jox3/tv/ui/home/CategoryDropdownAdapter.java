package com.jox3.tv.ui.home;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.jox3.tv.R;

import java.util.List;

/**
 * Lista vertical de categorías (reemplaza los chips horizontales que se
 * desplazaban de lado). Se usa dentro de un desplegable que se muestra/oculta
 * al tocar un botón "Categorías ▾", en vez de ocupar espacio fijo siempre.
 */
public class CategoryDropdownAdapter extends RecyclerView.Adapter<CategoryDropdownAdapter.RowHolder> {

    public interface OnCategorySelected {
        void onSelected(String category);
    }

    private final List<String> categories;
    private final OnCategorySelected listener;

    public CategoryDropdownAdapter(List<String> categories, OnCategorySelected listener) {
        this.categories = categories;
        this.listener = listener;
    }

    @NonNull
    @Override
    public RowHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_category_dropdown_row, parent, false);
        return new RowHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull RowHolder holder, int position) {
        String category = categories.get(position);
        holder.text.setText(category);
        holder.text.setOnClickListener(v -> {
            if (listener != null) listener.onSelected(category);
        });
    }

    @Override
    public int getItemCount() {
        return categories.size();
    }

    static class RowHolder extends RecyclerView.ViewHolder {
        TextView text;

        RowHolder(@NonNull View itemView) {
            super(itemView);
            text = (TextView) itemView;
        }
    }
}
