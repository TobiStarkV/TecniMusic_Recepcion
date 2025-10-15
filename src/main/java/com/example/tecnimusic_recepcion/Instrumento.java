package com.example.tecnimusic_recepcion;

public class Instrumento {
    private int id;
    private String nombreCliente;
    private String cedula;
    private String direccion;
    private String telefono;
    private String marca;
    private String modelo;
    private String serie;
    private String fechaIngreso;
    private String observaciones;

    public Instrumento(int id, String nombreCliente, String cedula, String direccion, String telefono, String marca, String modelo, String serie, String fechaIngreso, String observaciones) {
        this.id = id;
        this.nombreCliente = nombreCliente;
        this.cedula = cedula;
        this.direccion = direccion;
        this.telefono = telefono;
        this.marca = marca;
        this.modelo = modelo;
        this.serie = serie;
        this.fechaIngreso = fechaIngreso;
        this.observaciones = observaciones;
    }

    public int getId() {
        return id;
    }

    public String getNombreCliente() {
        return nombreCliente;
    }

    public String getCedula() {
        return cedula;
    }

    public String getDireccion() {
        return direccion;
    }

    public String getTelefono() {
        return telefono;
    }

    public String getMarca() {
        return marca;
    }

    public String getModelo() {
        return modelo;
    }

    public String getSerie() {
        return serie;
    }

    public String getFechaIngreso() {
        return fechaIngreso;
    }

    public String getObservaciones() {
        return observaciones;
    }
}
