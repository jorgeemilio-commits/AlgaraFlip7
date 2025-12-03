package com.servidormulti;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class ServidorMulti {

    static ConcurrentHashMap<String, UnCliente> clientes = new ConcurrentHashMap<String,UnCliente>();
    static AtomicInteger contadorClientes = new AtomicInteger(0); // Recomendado

    public static void main(String[] args) throws IOException {
        
        ContextoServidor contexto = new ContextoServidor(clientes);
        System.out.println("Servicios del servidor inicializados.");
        
        try (ServerSocket servidorSocket = new ServerSocket(8081)) {
            
            while (true) {
                Socket s = servidorSocket.accept();
                String clienteId = String.valueOf(contadorClientes.incrementAndGet());
            
                UnCliente unCliente = new UnCliente(s, clienteId, contexto);
                
                Thread hilo = new Thread(unCliente, "Cliente-" + clienteId);
                clientes.put(clienteId , unCliente);
                hilo.start();
                System.out.println("Se conecto el chango # " + clienteId );
            }
        } catch (IOException e) {
            System.err.println("Error al iniciar el servidor: " + e.getMessage());
        }
    }
}