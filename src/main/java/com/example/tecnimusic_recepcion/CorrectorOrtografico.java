package com.example.tecnimusic_recepcion;

import org.languagetool.JLanguageTool;
import org.languagetool.language.Spanish;
import org.languagetool.rules.RuleMatch;

import java.io.IOException;
import java.util.List;

public class CorrectorOrtografico {

    private static JLanguageTool languageTool = null;

    /**
     * Inicializa el corrector ortográfico. Esta es una operación costosa y debe
     * ser llamada en un hilo de fondo durante el arranque de la aplicación.
     */
    public static void inicializar() {
        if (languageTool == null) {
            languageTool = new JLanguageTool(new Spanish());
        }
    }

    public static List<RuleMatch> verificar(String texto) throws IOException {
        if (languageTool == null) {
            throw new IllegalStateException("El corrector ortográfico no ha sido inicializado. Por favor, llame a CorrectorOrtografico.inicializar() primero.");
        }
        return languageTool.check(texto);
    }
}
