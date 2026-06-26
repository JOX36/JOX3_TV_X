package com.jox3.tv.ui.home;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.jox3.tv.R;

import java.util.List;

public class CategoryChipAdapter extends RecyclerView.Adapter<CategoryChipAdapter.ChipHolder> {

    public interface OnCategorySelected {
        void onSelected(String category);
    }

    private final List<String> categories;
    private final OnCategorySelected listener;
    private String selectedCategory;

    public CategoryChipAdapter(List<String> categories, OnCategorySelected listener) {
        this.categories = categories;
        this.listener = listener;
        this.selectedCategory = categories.isEmpty() ? null : categories.get(0);
    }

    @NonNull
    @Override
    public ChipHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_category_chip, parent, false);
        return new ChipHolder((TextView) view);
    }

    @Override
    public void onBindViewHolder(@NonNull ChipHolder holder, int position) {
        String category = categories.get(position);
        holder.text.setText(category);

        boolean isSelected = category.equals(selectedCategory);
        holder.text.setBackgroundResource(
                isSelected ? R.drawable.bg_chip_active : R.drawable.bg_chip_inactive);
        holder.text.setTextColor(holder.text.getContext().getColor(
                isSelected ? R.color.text_primary : R.color.text_secondary));

        holder.itemView.setOnClickListener(v -> {
            String previous = selectedCategory;
            selectedCategory = category;

            int previousIndex = categories.indexOf(previous);
            if (previousIndex >= 0) notifyItemChanged(previousIndex);
            notifyItemChanged(position);

            if (listener != null) listener.onSelected(category);
        });
    }

    @Override
    public int getItemCount() {
        return categories.size();
    }

    /**
     * Actualiza la lista de categorías disponibles SIN perder la selección
     * actual: si la categoría que estaba marcada todavía existe en la
     * lista nueva, se mantiene seleccionada. Solo si ya no existe (por
     * ejemplo, cambiaste de cuenta) vuelve a la primera por defecto.
     */
    public void updateCategories(List<String> newCategories) {
        categories.clear();
        categories.addAll(newCategories);

        if (selectedCategory == null || !categories.contains(selectedCategory)) {
            selectedCategory = categories.isEmpty() ? null : categories.get(0);
        }
        notifyDataSetChanged();
    }

    /** Para que HomeActivity pueda mantenerse sincronizado con la selección real. */
    public String getSelectedCategory() {
        return selectedCategory;
    }

    static class ChipHolder extends RecyclerView.ViewHolder {
        TextView text;
        ChipHolder(@NonNull TextView itemView) {
            super(itemView);
            text = itemView;
        }
    }
}
