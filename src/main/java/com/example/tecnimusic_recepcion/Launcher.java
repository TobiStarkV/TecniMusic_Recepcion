package com.example.tecnimusic_recepcion;

import java.util.Locale;

public class Launcher {
    public static void main(String[] args) {
        // Establecer el idioma por defecto a Español
        Locale.setDefault(new Locale("es", "ES"));

        System.setProperty("jdk.xml.totalEntitySizeLimit", "200000");
        tecniMusic.main(args);
    }
}
