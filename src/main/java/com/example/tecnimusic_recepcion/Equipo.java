package com.example.tecnimusic_recepcion;

import java.math.BigDecimal;

public class Equipo {
    private Long id; // Nuevo campo para el ID de la base de datos
    private String tipo;
    private String marca;
    private String serie;
    private String modelo;
    private String falla;
    private BigDecimal costo;
    private String estadoFisico;
    private String accesorios;

    public Equipo() {}

    public Equipo(Long id, String tipo, String marca, String serie, String modelo, String falla, BigDecimal costo, String estadoFisico, String accesorios) {
        this.id = id;
        this.tipo = tipo;
        this.marca = marca;
        this.serie = serie;
        this.modelo = modelo;
        this.falla = falla;
        this.costo = costo;
        this.estadoFisico = estadoFisico;
        this.accesorios = accesorios;
    }

    // Constructor para la creación inicial (sin ID)
    public Equipo(String tipo, String marca, String serie, String modelo, String falla, BigDecimal costo, String estadoFisico, String accesorios) {
        this(null, tipo, marca, serie, modelo, falla, costo, estadoFisico, accesorios);
    }

    // Constructor anterior para mantener compatibilidad
    public Equipo(String tipo, String marca, String serie, String modelo, String falla, BigDecimal costo, String estadoFisico) {
        this(null, tipo, marca, serie, modelo, falla, costo, estadoFisico, "");
    }
    
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
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

    public String getAccesorios() {
        return accesorios;
    }

    public void setAccesorios(String accesorios) {
        this.accesorios = accesorios;
    }
}
