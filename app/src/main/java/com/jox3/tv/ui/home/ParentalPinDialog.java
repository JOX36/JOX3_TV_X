package com.jox3.tv.ui.home;

import android.app.AlertDialog;
import android.content.Context;
import android.text.InputType;
import android.widget.EditText;
import android.widget.Toast;

import com.jox3.tv.util.AppPrefs;

/**
 * Diálogo simple para pedir el PIN de control parental. Reutilizable desde
 * cualquier pantalla (ContentListActivity, CategoryGridActivity) que
 * necesite proteger el acceso a una categoría de adultos.
 */
public class ParentalPinDialog {

    public interface OnUnlocked {
        void onUnlocked();
    }

    /**
     * Si ya se desbloqueó en esta sesión, llama onUnlocked() de inmediato
     * sin mostrar ningún diálogo. Si no hay PIN configurado todavía, deja
     * pasar igual (no se puede exigir un PIN que el usuario nunca creó) y
     * además avisa para que lo configure en Ajustes.
     */
    public static void requireUnlock(Context context, AppPrefs prefs, OnUnlocked callback) {
        if (AppPrefs.isAdultUnlockedThisSession()) {
            callback.onUnlocked();
            return;
        }

        if (!prefs.hasParentalPin()) {
            Toast.makeText(context,
                    "No has configurado un PIN de control parental todavía (Ajustes)",
                    Toast.LENGTH_LONG).show();
            callback.onUnlocked();
            return;
        }

        EditText input = new EditText(context);
        input.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        input.setHint("PIN");

        new AlertDialog.Builder(context)
                .setTitle("Control parental")
                .setMessage("Esta sección está protegida. Ingresa el PIN para continuar.")
                .setView(input)
                .setPositiveButton("Continuar", (dialog, which) -> {
                    String attempt = input.getText().toString();
                    if (prefs.checkParentalPin(attempt)) {
                        AppPrefs.setAdultUnlockedThisSession(true);
                        callback.onUnlocked();
                    } else {
                        Toast.makeText(context, "PIN incorrecto", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }
}
