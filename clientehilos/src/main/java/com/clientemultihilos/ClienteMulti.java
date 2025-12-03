package com.clientemultihilos;

import java.io.IOException;
import java.net.Socket;
import java.net.ConnectException;

public class ClienteMulti {
    
    public static void main(String[] args) {
        String host = "localhost";
        int puerto = 8081;
        long tiempoEspera = 10000; // 10 segundos

        System.setProperty("file.encoding", "UTF-8");

        // Bucle de reconexión infinito
        while (true) {
            Socket s = null;
            Thread hiloParaMandar = null;
            Thread hiloParaRecibir = null;

            try {
                // --- 1. INTENTAR CONECTAR ---
                System.out.println("Intentando conectar al servidor en " + host + ":" + puerto + "...");
                s = new Socket(host, puerto);
                System.out.println("¡Conectado!"); // Se reinicia (no logueado)

                // --- 2. INICIAR HILOS DE COMUNICACIÓN ---
                ParaMandar paraMandar = new ParaMandar(s);
                hiloParaMandar = new Thread(paraMandar);
                hiloParaMandar.start();

                ParaRecibir paraRecibir = new ParaRecibir(s);
                hiloParaRecibir = new Thread(paraRecibir);
                hiloParaRecibir.start();

                // --- 3. ESPERAR A QUE LA CONEXIÓN MUERA ---
                // El hilo main se pausará aquí.
                hiloParaRecibir.join();

            } catch (ConnectException e) {
                // El servidor está offline, no se pudo conectar.
                System.out.println("El servidor está desconectado.");
            } catch (IOException e) {
                // El socket se rompió inesperadamente.
                System.out.println("Se perdió la conexión con el servidor.");
            } catch (InterruptedException e) {
                // Esto pasa si el hilo main es interrumpido
                System.out.println("Hilo principal interrumpido.");
                break; // Salir del bucle
            } finally {
                // --- 4. LIMPIEZA ---
                // No importa cómo salimos del 'try', la conexión está muerta.
                // Nos aseguramos de cerrar el socket (si es que existe).
                if (s != null) {
                    try { s.close(); } catch (IOException e) { /* ign */ }
                }
                
                // --- 5. ESPERAR Y REINTENTAR ---
                try {
                    System.out.println("Reintentando en " + (tiempoEspera / 1000) + " segundos...");
                    Thread.sleep(tiempoEspera);
                } catch (InterruptedException ie) {
                    break; // Salir del bucle si se interrumpe el sleep
                }
            }
        }
    }
}