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
    
    private final ManejadorAutenticacion manejadorAutenticacion;
    private final ManejadorSalas manejadorSalas; 
    
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

        this.manejadorAutenticacion = contexto.getManejadorAutenticacion();
        this.manejadorSalas = contexto.getManejadorSalas(); 
    }

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
    
    // Manejo Interno de Login/Logout
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
            mostrarMenuPrincipal(); // Muestra el menú principal al conectar
        } catch (IOException e) { System.err.println("Error de bienvenida: " + e.getMessage()); }

        while (true) {
            try {
                // Lee el mensaje del cliente
                String mensaje = entrada.readUTF();

                // Procesa el mensaje según el estado actual
                if (esEstadoDeSalas(estadoActual)) {
                    manejadorSalas.procesar(mensaje, this, salida);
                } else {
                    procesarAuth(mensaje, salida);
                }
                
            } catch (Exception ex) { 
                // El cliente se ha desconectado o hubo un error
                System.out.println("Cliente " + this.nombreUsuario + " se ha desconectado.");
                
                // Salir de la sala actual antes de eliminar al cliente
                if (this.salaActual != null) {
                    manejadorSalas.salirDelGrupoActual(this);
                }
                
                ServidorMulti.clientes.remove(this.clienteID); // Elimina al cliente de la lista activa
                
                try {
                    this.entrada.close();
                    this.salida.close();
                } catch (IOException e) { 
                    e.printStackTrace(); 
                }
                break;
            }
        }
    }
    
    // Verifica si el estado actual es uno relacionado con salas
    private boolean esEstadoDeSalas(EstadoMenu estado) {
        return estado == EstadoMenu.MENU_SALA_PRINCIPAL ||
               estado == EstadoMenu.MENU_UNIRSE_SALA ||
               estado == EstadoMenu.MENU_CREAR_SALA_NOMBRE ||
               estado == EstadoMenu.SALA_ACTIVA;
    }

    // Procesa la autenticación basada en el estado actual
    public void procesarAuth(String respuesta, DataOutputStream salida) throws IOException {
        
        switch (obtenerEstadoActual()) {
            
            case MENU_PRINCIPAL:
                if (respuesta.equals("1")) { 
                    establecerEstadoActual(EstadoMenu.INICIO_SESION_PEDIR_NOMBRE); // Cambia estado
                    salida.writeUTF("Introduce tu nombre de usuario:");
                } else if (respuesta.equals("2")) { 
                    establecerEstadoActual(EstadoMenu.REGISTRO_PEDIR_NOMBRE); // Cambia estado
                    salida.writeUTF("Introduce tu nombre de usuario (solo letras/números/acentos):");
                    /* 
                } else if (respuesta.equals("3")) { 
                    salida.writeUTF("Has entrado como Invitado.");
                    manejadorSalas.mostrarMenuSalaPrincipal(this, salida); // Muestra menú de salas
                    */
                } else {
                    salida.writeUTF("Opción no válida. Por favor, ingresa 1 o 2.");
                    mostrarMenuPrincipal();
                }
                break;
                
            case INICIO_SESION_PEDIR_NOMBRE: // Guarda el nombre temporalmente y pide la contraseña
                establecerNombreTemporal(respuesta);
                establecerEstadoActual(EstadoMenu.INICIO_SESION_PEDIR_CONTRASENA); // Cambia estado
                salida.writeUTF("Introduce tu contraseña:");
                break;
                
            case INICIO_SESION_PEDIR_CONTRASENA: // Intenta iniciar sesión con el nombre y contraseña proporcionados
                boolean exitoLogin = manejadorAutenticacion.manejarInicioSesionInternoPorMenu(
                    obtenerNombreTemporal(), 
                    respuesta, 
                    salida, 
                    this
                );
                // Limpia los datos temporales
                limpiarTemporales(); 
                
                if (exitoLogin) {
                    manejadorSalas.mostrarMenuSalaPrincipal(this, salida); // Muestra menú de salas
                } else {
                    mostrarMenuPrincipal(); // Vuelve al menú principal
                }
                break;
                
            case REGISTRO_PEDIR_NOMBRE: // Guarda el nombre temporalmente y pide la contraseña
                manejadorAutenticacion.manejarRegistroPorMenu_Paso1(respuesta, salida, this);
                break;
                
            case REGISTRO_PEDIR_CONTRASENA: // Guarda la contraseña temporalmente y pide confirmación
                manejadorAutenticacion.manejarRegistroPorMenu_Paso2(respuesta, salida, this);
                break;
                
            case REGISTRO_PEDIR_CONFIRMACION: // Intenta registrar al usuario
                boolean exitoRegistro = manejadorAutenticacion.manejarRegistroPorMenu_Paso3(respuesta, salida, this);
                if (exitoRegistro) {
                    manejadorSalas.mostrarMenuSalaPrincipal(this, salida); // Muestra menú de salas
                } else {
                    mostrarMenuPrincipal(); // Vuelve al menú principal
                }
                break;
                
            default: 
                salida.writeUTF("Error de estado interno. Volviendo al menú principal.");
                mostrarMenuPrincipal();
        }
    }

    // Muestra el menú principal al cliente
    public void mostrarMenuPrincipal() throws IOException {
        String menu = "\n" +
                      "--- Bienvenido al Servidor de Chat ---\n" +
                      "Tu nombre actual es: " + this.nombreUsuario + "\n" +
                      "Selecciona una opción:\n" +
                      "  1. Iniciar Sesión (Usuario registrado)\n" +
                      "  2. Registrar Nueva Cuenta\n" +
                      "  3. Entrar como Invitado (Desactivado) \n" +
                      "----------------------------------------------------\n" +
                      "Ingresa el número de tu opción (1 o 2):";
        this.salida.writeUTF(menu);
        this.estadoActual = EstadoMenu.MENU_PRINCIPAL;
    }
    public String getClienteID() {
        return this.clienteID;
    }

    public DataOutputStream getSalida() {
        return this.salida;
    }
}