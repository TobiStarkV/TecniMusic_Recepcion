package com.example.tecnimusic_recepcion;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Properties;

public class DatabaseConfig {

    private static final String CONFIG_FILE = "src/main/resources/config.properties";
    // Database properties
    private static final String KEY_HOST = "db.host";
    private static final String KEY_PORT = "db.port";
    private static final String KEY_DB_NAME = "db.name";
    private static final String KEY_USER = "db.user";
    private static final String KEY_PASSWORD = "db.password";
    // PDF properties
    private static final String KEY_PDF_FOOTER = "pdf.footer";
    // Local properties
    private static final String KEY_LOCAL_NOMBRE = "local.nombre";
    private static final String KEY_LOCAL_DIRECCION = "local.direccion";
    private static final String KEY_LOCAL_TELEFONO = "local.telefono";


    private Properties props;

    private String host;
    private String port;
    private String dbName;
    private String user;
    private String password;
    private String pdfFooter;
    private String localNombre;
    private String localDireccion;
    private String localTelefono;

    public DatabaseConfig() {
        props = new Properties();
        load();
    }

    private void load() {
        try (InputStream input = new FileInputStream(CONFIG_FILE)) {
            props.load(input);
            host = props.getProperty(KEY_HOST, "localhost");
            port = props.getProperty(KEY_PORT, "3306");
            dbName = props.getProperty(KEY_DB_NAME, "snipeit");
            user = props.getProperty(KEY_USER, "root");
            password = props.getProperty(KEY_PASSWORD, "");
            pdfFooter = props.getProperty(KEY_PDF_FOOTER, "");
            localNombre = props.getProperty(KEY_LOCAL_NOMBRE, "TecniMusic");
            localDireccion = props.getProperty(KEY_LOCAL_DIRECCION, "Dirección no configurada");
            localTelefono = props.getProperty(KEY_LOCAL_TELEFONO, "Teléfono no configurado");
        } catch (IOException ex) {
            // Si el archivo no existe, se usarán los valores por defecto y se creará al guardar.
            host = "localhost";
            port = "3306";
            dbName = "snipeit";
            user = "root";
            password = "";
            pdfFooter = "";
            localNombre = "TecniMusic";
            localDireccion = "Dirección no configurada";
            localTelefono = "Teléfono no configurado";
        }
    }

    public void save() {
        try (OutputStream output = new FileOutputStream(CONFIG_FILE)) {
            props.setProperty(KEY_HOST, host);
            props.setProperty(KEY_PORT, port);
            props.setProperty(KEY_DB_NAME, dbName);
            props.setProperty(KEY_USER, user);
            props.setProperty(KEY_PASSWORD, password);
            props.setProperty(KEY_PDF_FOOTER, pdfFooter);
            props.setProperty(KEY_LOCAL_NOMBRE, localNombre);
            props.setProperty(KEY_LOCAL_DIRECCION, localDireccion);
            props.setProperty(KEY_LOCAL_TELEFONO, localTelefono);
            props.store(output, "Application Configuration");
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }

    // Getters and Setters

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public String getPort() {
        return port;
    }

    public void setPort(String port) {
        this.port = port;
    }

    public String getDbName() {
        return dbName;
    }

    public void setDbName(String dbName) {
        this.dbName = dbName;
    }

    public String getUser() {
        return user;
    }

    public void setUser(String user) {
        this.user = user;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getPdfFooter() {
        return pdfFooter;
    }

    public void setPdfFooter(String pdfFooter) {
        this.pdfFooter = pdfFooter;
    }

    public String getLocalNombre() {
        return localNombre;
    }

    public void setLocalNombre(String localNombre) {
        this.localNombre = localNombre;
    }

    public String getLocalDireccion() {
        return localDireccion;
    }

    public void setLocalDireccion(String localDireccion) {
        this.localDireccion = localDireccion;
    }

    public String getLocalTelefono() {
        return localTelefono;
    }

    public void setLocalTelefono(String localTelefono) {
        this.localTelefono = localTelefono;
    }
}
