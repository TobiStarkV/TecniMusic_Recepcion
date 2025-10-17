package com.example.tecnimusic_recepcion;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Clase modelo para transportar los datos de una hoja de servicio.
 * Se utiliza para pasar la información necesaria al generador de PDF.
 */
public class HojaServicioData {

    private String numeroOrden;
    private LocalDate fechaOrden;
    private String clienteNombre;
    private String clienteTelefono;
    private String clienteDireccion;
    private String equipoTipo;
    private String equipoMarca;
    private String equipoSerie;
    private String equipoModelo;
    private String fallaReportada;
    private String informeCostos;
    private BigDecimal totalCostos;
    private LocalDate fechaEntrega;
    private String firmaAclaracion;
    private String aclaraciones;

    // Getters y Setters

    public String getNumeroOrden() {
        return numeroOrden;
    }

    public void setNumeroOrden(String numeroOrden) {
        this.numeroOrden = numeroOrden;
    }

    public LocalDate getFechaOrden() {
        return fechaOrden;
    }

    public void setFechaOrden(LocalDate fechaOrden) {
        this.fechaOrden = fechaOrden;
    }

    public String getClienteNombre() {
        return clienteNombre;
    }

    public void setClienteNombre(String clienteNombre) {
        this.clienteNombre = clienteNombre;
    }

    public String getClienteTelefono() {
        return clienteTelefono;
    }

    public void setClienteTelefono(String clienteTelefono) {
        this.clienteTelefono = clienteTelefono;
    }

    public String getClienteDireccion() {
        return clienteDireccion;
    }

    public void setClienteDireccion(String clienteDireccion) {
        this.clienteDireccion = clienteDireccion;
    }

    public String getEquipoTipo() {
        return equipoTipo;
    }

    public void setEquipoTipo(String equipoTipo) {
        this.equipoTipo = equipoTipo;
    }

    public String getEquipoMarca() {
        return equipoMarca;
    }

    public void setEquipoMarca(String equipoMarca) {
        this.equipoMarca = equipoMarca;
    }

    public String getEquipoSerie() {
        return equipoSerie;
    }

    public void setEquipoSerie(String equipoSerie) {
        this.equipoSerie = equipoSerie;
    }

    public String getEquipoModelo() {
        return equipoModelo;
    }

    public void setEquipoModelo(String equipoModelo) {
        this.equipoModelo = equipoModelo;
    }

    public String getFallaReportada() {
        return fallaReportada;
    }

    public void setFallaReportada(String fallaReportada) {
        this.fallaReportada = fallaReportada;
    }

    public String getInformeCostos() {
        return informeCostos;
    }

    public void setInformeCostos(String informeCostos) {
        this.informeCostos = informeCostos;
    }

    public BigDecimal getTotalCostos() {
        return totalCostos;
    }

    public void setTotalCostos(BigDecimal totalCostos) {
        this.totalCostos = totalCostos;
    }

    public LocalDate getFechaEntrega() {
        return fechaEntrega;
    }

    public void setFechaEntrega(LocalDate fechaEntrega) {
        this.fechaEntrega = fechaEntrega;
    }

    public String getFirmaAclaracion() {
        return firmaAclaracion;
    }

    public void setFirmaAclaracion(String firmaAclaracion) {
        this.firmaAclaracion = firmaAclaracion;
    }

    public String getAclaraciones() {
        return aclaraciones;
    }

    public void setAclaraciones(String aclaraciones) {
        this.aclaraciones = aclaraciones;
    }
}
