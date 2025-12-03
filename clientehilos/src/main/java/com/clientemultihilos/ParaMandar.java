package com.clientemultihilos;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.Socket;

public class ParaMandar implements Runnable {
    final BufferedReader teclado = new BufferedReader(new InputStreamReader(System.in));

    final DataOutputStream salida;
    private final Socket socket; // Guardamos el socket

    public ParaMandar(Socket s) throws IOException {
        this.salida = new DataOutputStream(s.getOutputStream());
        this.socket = s; // Asignamos el socket
    }

    @Override
    public void run() {
        String mensaje;
        try {
            // El bucle comprueba si hay null (Ctrl+Z)
            while ((mensaje = teclado.readLine()) != null) {
                
                // Comprobar si el comando es /cerrar
                if (mensaje.equalsIgnoreCase("/cerrar")) {
                    break; // Salir del bucle
                }

                salida.writeUTF(mensaje);
            }
            
            // Si el bucle termina (por /cerrar o Ctrl+Z), cerramos el socket.
            System.out.println("Desconectando...");
            socket.close();

        } catch (IOException ex) {
            // Esto pasará si el servidor se cae
            System.err.println("Error al enviar o conexión perdida.");
        }
    }
    
}