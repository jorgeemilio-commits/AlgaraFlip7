package com.servidormulti;

import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.Socket;

public class UnCliente implements Runnable {
    
    final DataOutputStream salida;
    final DataInputStream entrada;
    final String clienteID; 
    
    // Referencias a los manejadores
    private final ManejadorSalas manejadorSalas; 
    private final ManejadorMenu manejadorMenu; // Nueva referencia
    
    private String nombreUsuario; 
    private boolean logueado = false;

    private EstadoMenu estadoActual = EstadoMenu.MENU_PRINCIPAL; 
    private String nombreTemporal = null;
    private String contrasenaTemporal = null;
    private String salaActual = null;

    private final PrintWriter salidaUTF;
    private final BufferedReader entradaUTF;
    
    UnCliente(Socket s, String id, ContextoServidor contexto) throws java.io.IOException {
        this.salidaUTF = new PrintWriter(new OutputStreamWriter(s.getOutputStream(), "UTF-8"), true);
        this.entradaUTF = new BufferedReader(new InputStreamReader(s.getInputStream(), "UTF-8"));
        this.clienteID = id;
        this.nombreUsuario = "Invitado-" + id;

        this.salida = new DataOutputStream(s.getOutputStream());
        this.entrada = new DataInputStream(s.getInputStream());

        // Obtenemos los manejadores del contexto
        this.manejadorSalas = contexto.getManejadorSalas();
        this.manejadorMenu = contexto.getManejadorMenu();
    }

    // --- Métodos de Estado y Datos Temporales ---
    public synchronized EstadoMenu obtenerEstadoActual() { return estadoActual; }
    public synchronized void establecerEstadoActual(EstadoMenu estado) { this.estadoActual = estado; }
    public synchronized String obtenerNombreTemporal() { return nombreTemporal; }
    public synchronized void establecerNombreTemporal(String nombre) { this.nombreTemporal = nombre; }
    public synchronized String obtenerContrasenaTemporal() { return contrasenaTemporal; }
    public synchronized void establecerContrasenaTemporal(String password) { this.contrasenaTemporal = password; }
    public synchronized void limpiarTemporales() { 
        this.nombreTemporal = null; 
        this.contrasenaTemporal = null;
    }
    public synchronized String obtenerSalaActual() { return salaActual; }
    public synchronized void establecerSalaActual(String sala) { this.salaActual = sala; }
    
    public String getNombreUsuario() { return nombreUsuario; }
    public boolean estaLogueado() { return logueado; }
    
    // --- Manejo Interno de Login/Logout ---
    public boolean manejarLoginInterno(String nombre, String password) throws IOException {
        try {
            this.nombreUsuario = nombre; 
            this.logueado = true;
            return true;
        } catch (Exception e) {
            System.err.println("Error en login interno: " + e.getMessage());
            this.logueado = false;
            this.nombreUsuario = "Invitado-" + this.clienteID;
            return false;
        }
    }
    
    // Manejo Externo de Logout
    public void manejarLogoutInterno() {
        if (this.logueado) {
            manejadorSalas.salirDelGrupoActual(this);
            this.logueado = false;
            this.nombreUsuario = "Invitado-" + this.clienteID; 
        }
    }
    
    // Manejo Externo de Logout con Mensaje
    public void manejarLogout() throws IOException {
        manejarLogoutInterno();
        this.salida.writeUTF("Has cerrado sesión. Tu nombre es ahora '" + this.getNombreUsuario() + "'.");
    }

    @Override
    public void run() {
        try {
            // Delegamos al ManejadorMenu mostrar el menú inicial
            manejadorMenu.mostrarMenuPrincipal(this, salida); 
        } catch (IOException e) { System.err.println("Error de bienvenida: " + e.getMessage()); }

        while (true) {
            try {
                // Lee el mensaje del cliente
                String mensaje = entrada.readUTF();

                // Delegamos la lógica de decisión al ManejadorMenu
                if (manejadorMenu.esEstadoDeSalas(estadoActual)) {
                    manejadorSalas.procesar(mensaje, this, salida);
                } else {
                    // Si no es sala, es lógica de menú/auth
                    manejadorMenu.procesar(mensaje, this, salida);
                }
                
            } catch (Exception ex) { // El cliente se ha desconectado o hubo un error
                System.out.println("Cliente " + this.nombreUsuario + " se ha desconectado.");
                if (this.salaActual != null) {
                    manejadorSalas.salirDelGrupoActual(this);
                }
                ServidorMulti.clientes.remove(this.clienteID); // Elimina al cliente de la lista activa
                try {
                    this.entrada.close();
                    this.salida.close();
                } catch (IOException e) { e.printStackTrace(); } // Cierra flujos
                break;
            }
        }
    }
    
    public String getClienteID() {
        return this.clienteID;
    }

    public DataOutputStream getSalida() {
        return this.salida;
    }

    public ManejadorMenu getManejadorMenu() {
        return this.manejadorMenu;
    }
}