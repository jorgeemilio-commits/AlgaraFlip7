package com.clientemultihilos;

import java.io.DataInputStream;
import java.io.IOException;
import java.net.Socket;

public class ParaRecibir implements Runnable {
    
    final DataInputStream entrada;

    public ParaRecibir(Socket s) throws IOException {
        entrada = new DataInputStream(s.getInputStream());
    }

    @Override
    public void run() {
        String mensaje;
        
        try {
            while(true){
                mensaje = entrada.readUTF();
                System.out.println(mensaje);
            }
        } catch (IOException ex) {
            System.out.println("Conexión con el servidor cerrada.");
        }
    }

}