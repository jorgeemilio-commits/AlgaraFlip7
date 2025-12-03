package com.servidormulti;

import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.Socket;
import com.servidormulti.EstadoMenu;
import com.servidormulti.ManejadorAutenticacion;

public class UnCliente implements Runnable {
    
    final DataOutputStream salida;
    final DataInputStream entrada;
    final String clienteID; 
    
    private final ManejadorMensajes manejadorMensajes;
    private final ManejadorAutenticacion manejadorAutenticacion; 
    
    private static final int LIMITE_MENSAJES_INVITADO = 3;
    
    private String nombreUsuario; 
    private int mensajesEnviados = 0;
    private boolean logueado = false;

    // --- CAMPOS DE MÁQUINA DE ESTADOS ---
    private EstadoMenu estadoActual = EstadoMenu.MENU_PRINCIPAL; 
    private String nombreTemporal = null;
    private String contrasenaTemporal = null;
    // ------------------------------------

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
        this.manejadorAutenticacion = contexto.getManejadorAutenticacion();
    }

    // --- MÉTODOS PÚBLICOS DE GESTIÓN DE ESTADO ---
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
    
    // --- MÉTODOS PÚBLICOS DE CLIENTE ---
    public String getNombreUsuario() { return nombreUsuario; }
    public int getMensajesEnviados() { return mensajesEnviados; }
    public void incrementarMensajesEnviados() { this.mensajesEnviados++; }
    public boolean estaLogueado() { return logueado; }
    
    // **MÉTODO RESTAURADO: maneja el cambio de estado de usuario después de la autenticación.**
    public boolean manejarLoginInterno(String nombre, String password) throws IOException {
        
        try {
            this.nombreUsuario = nombre; 
            this.logueado = true;
            
            // Unir al usuario al grupo 'Todos' (requerido para el chat)
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
    
    public void manejarLogout() throws IOException {
        if (!this.estaLogueado()) {
            this.salida.writeUTF("Ya estás desconectado. Tu nombre es: " + this.getNombreUsuario());
            return;
        }
        manejarLogoutInterno();
        this.salida.writeUTF("Has cerrado sesión. Tu nombre es ahora '" + this.getNombreUsuario() + "'.");
    }

    @Override
    public void run() {
        try {
            mostrarMenuPrincipal();
        } catch (IOException e) { System.err.println("Error de bienvenida: " + e.getMessage()); }

        while (true) {
            try {
                String mensaje = entrada.readUTF();

                // 1. Manejo del único "comando" restante: /logout
                if (mensaje.trim().equalsIgnoreCase("/logout")) {
                    manejarLogout(); 
                    if (!this.estaLogueado()) {
                        mostrarMenuPrincipal();
                    }
                } 
                // 2. Manejo de mensajes de chat (solo si estamos en modo CHAT)
                else if (this.estadoActual == EstadoMenu.MENU_CHAT) {
                    
                    if (!this.logueado) {
                        if (this.mensajesEnviados >= LIMITE_MENSAJES_INVITADO) {
                            this.salida.writeUTF("Límite de mensajes alcanzado. Por favor, inicia sesión.");
                            continue;
                        }
                        this.incrementarMensajesEnviados();
                    }
                    manejadorMensajes.enrutarMensaje(this, mensaje);
                } 
                // 3. Manejo de respuestas de menú
                else {
                    procesarRespuestaEnMenu(mensaje, salida);
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
    
    public void procesarRespuestaEnMenu(String respuesta, DataOutputStream salida) throws IOException {
        
        switch (obtenerEstadoActual()) {
            
            case MENU_PRINCIPAL:
                if (respuesta.equals("1")) { // Iniciar Sesión
                    establecerEstadoActual(EstadoMenu.INICIO_SESION_PEDIR_NOMBRE);
                    salida.writeUTF("Introduce tu nombre de usuario:");
                } else if (respuesta.equals("2")) { // Registrar
                    establecerEstadoActual(EstadoMenu.REGISTRO_PEDIR_NOMBRE);
                    salida.writeUTF("Introduce tu nombre de usuario (solo letras/números/acentos):");
                } else if (respuesta.equals("3")) { // Entrar como Invitado
                    establecerEstadoActual(EstadoMenu.MENU_CHAT);
                    salida.writeUTF("Has entrado como Invitado. Puedes enviar hasta " + LIMITE_MENSAJES_INVITADO + " mensajes. Escribe /logout para volver al menú principal.");
                    mostrarMenuChat();
                } else {
                    salida.writeUTF("Opción no válida. Por favor, ingresa 1, 2, o 3.");
                    mostrarMenuPrincipal();
                }
                break;
                
            case INICIO_SESION_PEDIR_NOMBRE:
                establecerNombreTemporal(respuesta);
                establecerEstadoActual(EstadoMenu.INICIO_SESION_PEDIR_CONTRASENA);
                salida.writeUTF("Introduce tu contraseña:");
                break;
                
            case INICIO_SESION_PEDIR_CONTRASENA:
                boolean exitoLogin = manejadorAutenticacion.manejarInicioSesionInternoPorMenu(
                    obtenerNombreTemporal(), 
                    respuesta, 
                    salida, 
                    this
                );
                
                limpiarTemporales(); 
                
                if (exitoLogin) {
                    mostrarMenuChat();
                } else {
                    mostrarMenuPrincipal(); 
                }
                break;
                
            case REGISTRO_PEDIR_NOMBRE:
                manejadorAutenticacion.manejarRegistroPorMenu_Paso1(respuesta, salida, this);
                break;
                
            case REGISTRO_PEDIR_CONTRASENA:
                manejadorAutenticacion.manejarRegistroPorMenu_Paso2(respuesta, salida, this);
                break;
                
            case REGISTRO_PEDIR_CONFIRMACION:
                manejadorAutenticacion.manejarRegistroPorMenu_Paso3(respuesta, salida, this);
                break;
                
            default: 
                salida.writeUTF("Error de estado. Volviendo al menú principal.");
                mostrarMenuPrincipal();
        }
    }

    public void mostrarMenuPrincipal() throws IOException {
        String menu = "\n" +
                      "--- Bienvenido al Servidor de Chat ---\n" +
                      "Tu nombre actual es: " + this.nombreUsuario + "\n" +
                      "Selecciona una opción:\n" +
                      "  1. Iniciar Sesión (Usuario registrado)\n" +
                      "  2. Registrar Nueva Cuenta\n" +
                      "  3. Entrar como Invitado (Acceso limitado)\n" +
                      "----------------------------------------------------\n" +
                      "Ingresa el número de tu opción (1, 2 o 3):";
        this.salida.writeUTF(menu);
        this.estadoActual = EstadoMenu.MENU_PRINCIPAL;
    }
    
    public void mostrarMenuChat() throws IOException {
        String estado = this.logueado ? "Logueado" : "Invitado";
        String limite = this.logueado ? "Sin límite de mensajes." : "Límite de " + LIMITE_MENSAJES_INVITADO + " mensajes. Escribe /logout para volver al menú principal.";
        
        String menu = "\n" +
                      "--- MODO CHAT - " + estado + " como: " + this.nombreUsuario + " ---\n" +
                      "  * Escribe tu mensaje y presiona Enter.\n" +
                      "  * Para salir: Escribe /logout\n" +
                      "  " + limite + "\n" +
                      "----------------------------------------------------\n" +
                      "¡Comienza a chatear!";
        this.salida.writeUTF(menu);
        this.estadoActual = EstadoMenu.MENU_CHAT;
    }
}