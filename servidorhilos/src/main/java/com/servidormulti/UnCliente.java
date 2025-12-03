package com.servidormulti;

import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class UnCliente implements Runnable {
    
    final DataOutputStream salida;
    final DataInputStream entrada;
    final String clienteID; 
    
    private final ManejadorMensajes manejadorMensajes;
    private final EnrutadorComandos enrutadorComandos; 
    
    private static final int LIMITE_MENSAJES_INVITADO = 3;
    
    private String nombreUsuario; 
    private int mensajesEnviados = 0;
    private boolean logueado = false;

    private volatile String oponentePendiente = null;
    private final ConcurrentHashMap<String, Object> juegosActivos = new ConcurrentHashMap<>();
    
    private final PrintWriter salidaUTF;
    private final BufferedReader entradaUTF;
    
    UnCliente(Socket s, String id, ContextoServidor contexto) throws java.io.IOException {
        this.salidaUTF = new PrintWriter(new OutputStreamWriter(s.getOutputStream(), "UTF-8"), true);
        this.entradaUTF = new BufferedReader(new InputStreamReader(s.getInputStream(), "UTF-8"));
        this.clienteID = id;
        this.nombreUsuario = "Invitado-" + id;

        this.salida = new DataOutputStream(s.getOutputStream());
        this.entrada = new DataInputStream(s.getInputStream());

        this.manejadorMensajes = contexto.getManejadorMensajes();
        this.enrutadorComandos = contexto.getEnrutadorComandos();
    }

    public String getNombreUsuario() { return nombreUsuario; }
    public int getMensajesEnviados() { return mensajesEnviados; }
    public void incrementarMensajesEnviados() { this.mensajesEnviados++; }
    public boolean estaLogueado() { return logueado; }
    public synchronized String getOponentePendiente() { return oponentePendiente; }
    public synchronized void setOponentePendiente(String nombre) { this.oponentePendiente = nombre; }
    public synchronized Object getJuegoConID(String juegoID) { return juegosActivos.get(juegoID); }
    public synchronized void agregarJuego(String juegoID, Object juego) { juegosActivos.put(juegoID, juego); }
    public synchronized void removerJuego(String juegoID) { juegosActivos.remove(juegoID); }
    public synchronized boolean estaEnJuego() { return !this.juegosActivos.isEmpty(); }
    @SuppressWarnings("unchecked")
    public synchronized ConcurrentHashMap<String, Object> getJuegosActivos() { return (ConcurrentHashMap<String, Object>) juegosActivos; }
    
    
    /**
     * Aplica el estado de login y une al usuario al grupo 'Todos'.
     * @param nombre El nombre de usuario.
     * @param password La contraseña.
     * @return true si el login interno fue exitoso.
     * @throws IOException 
     */
    public boolean manejarLoginInterno(String nombre, String password) throws IOException {
        
        try {
            this.nombreUsuario = nombre; 
            this.logueado = true;
            
            // Esta línea ahora debería funcionar si GrupoDB.unirseGrupo existe.
            new GrupoDB().unirseGrupo("Todos", nombre); 
            
            return true;

        } catch (Exception e) {
            System.err.println("Error en la fase final de login para " + nombre + ": " + e.getMessage());
            this.logueado = false;
            this.nombreUsuario = "Invitado-" + this.clienteID;
            return false;
        }
    }
    
    public void manejarLogoutInterno() {
        if (this.logueado) {
            this.logueado = false;
            this.nombreUsuario = "Invitado-" + this.clienteID; 
            this.mensajesEnviados = 0;
        }
    }
    
    @Override
    public void run() {
        try {
            this.salida.writeUTF("Bienvenido. Tu nombre actual es: " + this.nombreUsuario + "\n" +
                                 "--- Comandos Básicos ---\n" +
                                 "  Mensaje a 'Todos': Hola a todos\n" +
                                 "  Mensaje a Grupo:   #NombreGrupo Hola grupo (Solo logueado)\n" +
                                 "  /login             - Inicia sesión\n" +
                                 "  /registrar         - Crea una nueva cuenta\n" +
                                 "--- Para más comandos, escribe: /ayuda ---");

        } catch (IOException e) { System.err.println("Error de bienvenida: " + e.getMessage()); }

        while (true) {
            try {
                String mensaje = entrada.readUTF();

                if (mensaje.startsWith("/")) {
                    
                    if (!this.logueado) {
                        String comando = mensaje.trim().split(" ", 2)[0].toLowerCase();

                        boolean esComandoExcluido = comando.equals("/login") || 
                                                    comando.equals("/registrar") ||
                                                    comando.equals("/ayuda") ||
                                                    comando.equals("/conectados");

                        if (!esComandoExcluido) {
                            if (this.mensajesEnviados >= LIMITE_MENSAJES_INVITADO) {
                                this.salida.writeUTF("Límite de acciones alcanzado. Por favor, inicia sesión para continuar con /login o /register.");
                                continue;
                            }
                            this.incrementarMensajesEnviados();
                        }
                    }
                    
                    enrutadorComandos.procesar(mensaje, entrada, salida, this);

                } else {
                    
                    if (!this.logueado) {
                        if (this.mensajesEnviados >= LIMITE_MENSAJES_INVITADO) {
                            this.salida.writeUTF("Límite de mensajes alcanzado. Por favor, inicia sesión.");
                            continue;
                        }
                        this.incrementarMensajesEnviados();
                    }
                    
                    manejadorMensajes.enrutarMensaje(this, mensaje);
                }
                
            } catch (Exception ex) {
                System.out.println("Cliente " + this.nombreUsuario + " se ha desconectado.");
                
                ServidorMulti.clientes.remove(this.clienteID); 
                try {
                    this.entrada.close();
                    this.salida.close();
                } catch (IOException e) { e.printStackTrace(); }
                break;
            }
        }
    }
}