package com.example.tecnimusic_recepcion;

import java.util.prefs.Preferences;

public class DatabaseConfig {

    private static final String PREF_NODE = "com/example/tecnimusic_recepcion";
    private static final String KEY_HOST = "db_host";
    private static final String KEY_PORT = "db_port";
    private static final String KEY_DB_NAME = "db_name";
    private static final String KEY_USER = "db_user";
    private static final String KEY_PASSWORD = "db_password";

    private Preferences prefs;

    private String host;
    private String port;
    private String dbName;
    private String user;
    private String password;

    public DatabaseConfig() {
        prefs = Preferences.userRoot().node(PREF_NODE);
        load();
    }

    private void load() {
        host = prefs.get(KEY_HOST, "localhost");
        port = prefs.get(KEY_PORT, "3306");
        dbName = prefs.get(KEY_DB_NAME, "snipeit");
        user = prefs.get(KEY_USER, "root");
        password = prefs.get(KEY_PASSWORD, "");
    }

    public void save() {
        prefs.put(KEY_HOST, host);
        prefs.put(KEY_PORT, port);
        prefs.put(KEY_DB_NAME, dbName);
        prefs.put(KEY_USER, user);
        prefs.put(KEY_PASSWORD, password);
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
