package com.example.tecnimusic_recepcion;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

public class DatabaseConfig {

    // Ruta a la carpeta de configuración en el directorio home del usuario
    private static final Path CONFIG_DIR = Paths.get(System.getProperty("user.home"), ".TecniMusic_Recepcion");
    private static final Path CONFIG_FILE_PATH = CONFIG_DIR.resolve("config.properties");
    // Ruta al archivo de configuración por defecto dentro del JAR
    private static final String DEFAULT_CONFIG_RESOURCE = "/com/example/tecnimusic_recepcion/config.properties";

    // Database properties
    private static final String KEY_HOST = "db.host";
    private static final String KEY_PORT = "db.port";
    private static final String KEY_DB_NAME = "db.name";
    private static final String KEY_USER = "db.user";
    private static final String KEY_PASSWORD = "db.password";
    // Eliminamos las claves de PDF y Local de aquí

    private Properties props;

    private String host;
    private String port;
    private String dbName;
    private String user;
    private String password;
    // Eliminamos los campos de PDF y Local de aquí

    public DatabaseConfig() {
        props = new Properties();
        load();
    }

    private void load() {
        // Primero, intentar cargar desde el archivo de configuración del usuario
        if (Files.exists(CONFIG_FILE_PATH)) {
            try (InputStream input = new FileInputStream(CONFIG_FILE_PATH.toFile())) {
                props.load(input);
            } catch (IOException ex) {
                System.err.println("Error al cargar el archivo de configuración del usuario: " + ex.getMessage());
            }
        } else {
            // Si no existe, cargar la configuración por defecto desde los recursos del JAR
            try (InputStream input = getClass().getResourceAsStream(DEFAULT_CONFIG_RESOURCE)) {
                if (input != null) {
                    props.load(input);
                }
            } catch (IOException ex) {
                System.err.println("Error al cargar la configuración por defecto: " + ex.getMessage());
            }
        }

        // Asignar valores desde las propiedades, con valores por defecto si no se encuentran
        host = props.getProperty(KEY_HOST, "localhost");
        port = props.getProperty(KEY_PORT, "3306");
        dbName = props.getProperty(KEY_DB_NAME, "snipeit");
        user = props.getProperty(KEY_USER, "root");
        password = props.getProperty(KEY_PASSWORD, "");
        // Eliminamos la asignación de pdfFooter, localNombre, etc.
    }

    public void save() {
        try {
            // Asegurarse de que el directorio de configuración exista
            if (!Files.exists(CONFIG_DIR)) {
                Files.createDirectories(CONFIG_DIR);
            }

            // Guardar las propiedades en el archivo de configuración del usuario
            try (OutputStream output = new FileOutputStream(CONFIG_FILE_PATH.toFile())) {
                props.setProperty(KEY_HOST, host);
                props.setProperty(KEY_PORT, port);
                props.setProperty(KEY_DB_NAME, dbName);
                props.setProperty(KEY_USER, user);
                props.setProperty(KEY_PASSWORD, password);
                // Eliminamos el guardado de pdfFooter, localNombre, etc.
                props.store(output, "TecniMusic Recepcion User Configuration");
            }
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }

    // Getters and Setters (solo para las propiedades de la DB)

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

    // Eliminamos los getters y setters para pdfFooter, localNombre, etc.
}
