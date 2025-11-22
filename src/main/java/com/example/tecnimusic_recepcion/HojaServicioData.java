package com.example.tecnimusic_recepcion;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

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
    // Campos individuales (retrocompatibilidad)
    private String equipoTipo;
    private String equipoMarca;
    private String equipoSerie;
    private String equipoModelo;
    private String fallaReportada;
    private String estadoFisico;
    private String accesorios;
    private String informeCostos;
    private BigDecimal totalCostos;
    private BigDecimal anticipo; // Nuevo campo para el anticipo
    private LocalDate fechaEntrega;
    private String firmaAclaracion;
    private String aclaraciones;

    // Nuevos campos para el cierre
    private String informeTecnico;
    private String estado;


    // Nueva lista de equipos para soportar múltiples equipos en la misma hoja
    private List<Equipo> equipos = new ArrayList<>();

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

    public BigDecimal getAnticipo() {
        return anticipo;
    }

    public void setAnticipo(BigDecimal anticipo) {
        this.anticipo = anticipo;
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

    public List<Equipo> getEquipos() {
        return equipos;
    }

    public void setEquipos(List<Equipo> equipos) {
        this.equipos = equipos != null ? equipos : new ArrayList<>();
        // Mantener compatibilidad: si hay al menos un equipo, rellenar los campos individuales con el primero
        if (!this.equipos.isEmpty()) {
            Equipo primero = this.equipos.get(0);
            this.equipoTipo = primero.getTipo();
            this.equipoMarca = primero.getMarca();
            this.equipoModelo = primero.getModelo();
            this.equipoSerie = primero.getSerie();
            this.fallaReportada = primero.getFalla();
            this.estadoFisico = primero.getEstadoFisico();
            this.accesorios = primero.getAccesorios();
        }
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

    public String getInformeTecnico() {
        return informeTecnico;
    }

    public void setInformeTecnico(String informeTecnico) {
        this.informeTecnico = informeTecnico;
    }

    public String getEstado() {
        return estado;
    }

    public void setEstado(String estado) {
        this.estado = estado;
    }
}
