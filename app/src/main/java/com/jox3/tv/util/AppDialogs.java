package com.jox3.tv.util;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Shader;
import android.graphics.drawable.ColorDrawable;
import android.text.TextPaint;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;

import com.jox3.tv.R;

/**
 * Diálogo de confirmación con el estilo visual de la app: card oscura,
 * borde cian, título con degradado de marca, botón principal con el
 * gradiente cian->morado y botón secundario en outline gris.
 *
 * Reemplaza a los AlertDialog.Builder por defecto de Android (fondo
 * blanco, letras negras, botones de texto plano) que no combinaban con
 * el resto de la app. Se armó como helper genérico para no repetir el
 * mismo inflado + Shader del título cada vez que se necesite un diálogo
 * nuevo con este estilo (ej. "¿Salir de la app?", "Continuar viendo").
 */
public class AppDialogs {

    private AppDialogs() { }

    public interface OnConfirmAction {
        void onPrimary();
        /** Se llama al tocar el botón secundario. Puede quedar vacío si no hace falta nada. */
        void onSecondary();
    }

    /**
     * @param icon           Emoji opcional para arriba del título (null o "" para ocultarlo).
     * @param secondaryLabel Si es null, el botón secundario se oculta por completo (diálogo de un solo botón).
     * @param cancelable     Si se puede cerrar tocando fuera o con el botón atrás.
     */
    public static void showConfirm(Activity activity, String icon, String title, String message,
                                    String primaryLabel, String secondaryLabel,
                                    boolean cancelable, OnConfirmAction action) {
        View view = LayoutInflater.from(activity).inflate(R.layout.dialog_app_confirm, null);

        TextView tvIcon = view.findViewById(R.id.dialog_icon);
        TextView tvTitle = view.findViewById(R.id.dialog_title);
        TextView tvMessage = view.findViewById(R.id.dialog_message);
        TextView btnPrimary = view.findViewById(R.id.dialog_btn_primary);
        TextView btnSecondary = view.findViewById(R.id.dialog_btn_secondary);

        if (icon != null && !icon.isEmpty()) {
            tvIcon.setText(icon);
        } else {
            tvIcon.setVisibility(View.GONE);
        }

        tvTitle.setText(title);
        applyBrandGradient(tvTitle);
        tvMessage.setText(message);
        btnPrimary.setText(primaryLabel);

        AlertDialog dialog = new AlertDialog.Builder(activity)
                .setView(view)
                .setCancelable(cancelable)
                .create();

        // Sin esto, Android pinta un fondo blanco cuadrado detrás de
        // nuestra card (el fondo por defecto del diálogo del sistema),
        // que se asomaría por las esquinas redondeadas de bg_dialog_app.
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }

        btnPrimary.setOnClickListener(v -> {
            dialog.dismiss();
            if (action != null) action.onPrimary();
        });

        if (secondaryLabel != null) {
            btnSecondary.setText(secondaryLabel);
            btnSecondary.setOnClickListener(v -> {
                dialog.dismiss();
                if (action != null) action.onSecondary();
            });
        } else {
            btnSecondary.setVisibility(View.GONE);
        }

        dialog.show();
    }

    /**
     * Mismo Shader cian->azul->morado que ya se usa en el título del
     * reproductor (PlayerActivity.setChannelTitle), centralizado acá
     * para no repetirlo a mano en cada diálogo nuevo.
     */
    private static void applyBrandGradient(TextView textView) {
        String text = textView.getText() != null ? textView.getText().toString() : "";
        TextPaint paint = textView.getPaint();
        float textWidth = paint.measureText(text);
        if (textWidth <= 0) textWidth = 1;
        Shader shader = new LinearGradient(
                0, 0, textWidth, 0,
                new int[]{0xFF00E5FF, 0xFF2979FF, 0xFF7C4DFF},
                null,
                Shader.TileMode.CLAMP);
        paint.setShader(shader);
        textView.invalidate();
    }
}
