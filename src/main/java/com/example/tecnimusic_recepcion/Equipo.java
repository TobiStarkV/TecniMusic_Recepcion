package com.example.tecnimusic_recepcion;

import java.math.BigDecimal;

public class Equipo {
    private String tipo;
    private String marca;
    private String serie;
    private String modelo;
    private String falla;
    private BigDecimal costo;
    private String estadoFisico; // Nuevo campo

    public Equipo() {}

    public Equipo(String tipo, String marca, String serie, String modelo, String falla, BigDecimal costo, String estadoFisico) {
        this.tipo = tipo;
        this.marca = marca;
        this.serie = serie;
        this.modelo = modelo;
        this.falla = falla;
        this.costo = costo;
        this.estadoFisico = estadoFisico;
    }

    // Constructor anterior para mantener compatibilidad
    public Equipo(String tipo, String marca, String serie, String modelo, String falla, BigDecimal costo) {
        this(tipo, marca, serie, modelo, falla, costo, "");
    }
    
    public String getTipo() {
        return tipo;
    }

    public void setTipo(String tipo) {
        this.tipo = tipo;
    }

    public String getMarca() {
        return marca;
    }

    public void setMarca(String marca) {
        this.marca = marca;
    }

    public String getSerie() {
        return serie;
    }

    public void setSerie(String serie) {
        this.serie = serie;
    }

    public String getModelo() {
        return modelo;
    }

    public void setModelo(String modelo) {
        this.modelo = modelo;
    }

    public String getFalla() {
        return falla;
    }

    public void setFalla(String falla) {
        this.falla = falla;
    }

    public BigDecimal getCosto() {
        return costo;
    }

    public void setCosto(BigDecimal costo) {
        this.costo = costo;
    }

    public String getEstadoFisico() {
        return estadoFisico;
    }

    public void setEstadoFisico(String estadoFisico) {
        this.estadoFisico = estadoFisico;
    }
}
