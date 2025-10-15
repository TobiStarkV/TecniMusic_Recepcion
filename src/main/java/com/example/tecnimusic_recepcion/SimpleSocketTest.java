package com.example.tecnimusic_recepcion;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;

public class SimpleSocketTest {

    public static void main(String[] args) {
        String host = "192.168.100.222";
        int port = 3306;
        int timeoutMs = 10000; // 10 segundos

        System.out.println("--- Iniciando prueba de conexión simple ---");
        System.out.println("Intentando conectar a: " + host + ":" + port + " con un timeout de " + timeoutMs + "ms");

        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), timeoutMs);
            System.out.println("\n*************************************");
            System.out.println(">>> ÉXITO: ¡La conexión fue exitosa! <<<");
            System.out.println("*************************************");
            System.out.println("\nEsto confirma que Java SÍ puede establecer una conexión de red básica al destino.");
            System.out.println("Si la conexión de la base de datos aún falla, el problema está en otro lado (driver JDBC, credenciales, permisos en la BD, etc.).");

        } catch (SocketTimeoutException e) {
            System.err.println("\n*****************************************************************");
            System.err.println(">>> FALLO (TIMEOUT): La conexión excedió los " + (timeoutMs / 1000) + " segundos. <<<");
            System.err.println("*****************************************************************");
            System.err.println("\nCausa probable: Un software en su PC (Firewall, Antivirus, VPN, etc.) está bloqueando la conexión saliente de 'javaw.exe'.");
            System.err.println("Aunque otras herramientas (ej. PowerShell, cliente de BD) funcionen, este software podría tener reglas específicas para aplicaciones Java.");

        } catch (IOException e) {
            System.err.println("\n*****************************************************************");
            System.err.println(">>> FALLO (ERROR DE E/S): " + e.getMessage() + " <<<");
            System.err.println("*****************************************************************");
            System.err.println("\nCausa probable: El servidor fue alcanzado, pero rechazó la conexión. Verifique que el servicio (ej. MySQL) esté corriendo en el puerto " + port + " y que el firewall del servidor permita conexiones desde su IP.");
        }
    }
}
