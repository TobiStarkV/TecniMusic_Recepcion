package com.example.tecnimusic_recepcion;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Properties;

public class DatabaseConfig {

    private static final String CONFIG_FILE = "src/main/resources/config.properties";
    private static final String KEY_HOST = "db.host";
    private static final String KEY_PORT = "db.port";
    private static final String KEY_DB_NAME = "db.name";
    private static final String KEY_USER = "db.user";
    private static final String KEY_PASSWORD = "db.password";

    private Properties props;

    private String host;
    private String port;
    private String dbName;
    private String user;
    private String password;

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
        } catch (IOException ex) {
            // Si el archivo no existe, se usarán los valores por defecto y se creará al guardar.
            host = "localhost";
            port = "3306";
            dbName = "snipeit";
            user = "root";
            password = "";
        }
    }

    public void save() {
        try (OutputStream output = new FileOutputStream(CONFIG_FILE)) {
            props.setProperty(KEY_HOST, host);
            props.setProperty(KEY_PORT, port);
            props.setProperty(KEY_DB_NAME, dbName);
            props.setProperty(KEY_USER, user);
            props.setProperty(KEY_PASSWORD, password);
            props.store(output, "Database Configuration");
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
}
