package com.example.tecnimusic_recepcion;

import org.languagetool.JLanguageTool;
import org.languagetool.language.Spanish;
import org.languagetool.rules.RuleMatch;

import java.io.IOException;
import java.util.List;

public class CorrectorOrtografico {

    private static final JLanguageTool languageTool = new JLanguageTool(new Spanish());

    public static List<RuleMatch> verificar(String texto) throws IOException {
        return languageTool.check(texto);
    }
}
