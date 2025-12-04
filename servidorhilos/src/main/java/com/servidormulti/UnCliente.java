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
        // ESTA LÍNEA AHORA FUNCIONARÁ PORQUE CONTEXTOSERVIDOR YA TIENE EL GETTER
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
    
    // MÉTODO QUE FALTABA (SOLUCIONA IMAGEN 3)
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
    
    public void manejarLogoutInterno() {
        if (this.logueado) {
            manejadorSalas.salirDelGrupoActual(this);
            this.logueado = false;
            this.nombreUsuario = "Invitado-" + this.clienteID; 
        }
    }
    
    public void manejarLogout() throws IOException {
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

                if (esEstadoDeSalas(estadoActual)) {
                    manejadorSalas.procesar(mensaje, this, salida);
                } else {
                    procesarAuth(mensaje, salida);
                }
                
            } catch (Exception ex) {
                System.out.println("Cliente " + this.nombreUsuario + " se ha desconectado.");
                if (this.salaActual != null) {
                    manejadorSalas.salirDelGrupoActual(this);
                }
                ServidorMulti.clientes.remove(this.clienteID); 
                try {
                    this.entrada.close();
                    this.salida.close();
                } catch (IOException e) { e.printStackTrace(); }
                break;
            }
        }
    }
    
    private boolean esEstadoDeSalas(EstadoMenu estado) {
        return estado == EstadoMenu.MENU_SALA_PRINCIPAL ||
               estado == EstadoMenu.MENU_UNIRSE_SALA ||
               estado == EstadoMenu.MENU_CREAR_SALA_NOMBRE ||
               estado == EstadoMenu.SALA_ACTIVA;
    }

    public void procesarAuth(String respuesta, DataOutputStream salida) throws IOException {
        
        switch (obtenerEstadoActual()) {
            
            case MENU_PRINCIPAL:
                if (respuesta.equals("1")) { 
                    establecerEstadoActual(EstadoMenu.INICIO_SESION_PEDIR_NOMBRE);
                    salida.writeUTF("Introduce tu nombre de usuario:");
                } else if (respuesta.equals("2")) { 
                    establecerEstadoActual(EstadoMenu.REGISTRO_PEDIR_NOMBRE);
                    salida.writeUTF("Introduce tu nombre de usuario (solo letras/números/acentos):");
                } else if (respuesta.equals("3")) { 
                    salida.writeUTF("Has entrado como Invitado.");
                    manejadorSalas.mostrarMenuSalaPrincipal(this, salida);
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
                    manejadorSalas.mostrarMenuSalaPrincipal(this, salida);
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
                // ESTA LÍNEA AHORA FUNCIONARÁ (SOLUCIONA IMAGEN 1)
                boolean exitoRegistro = manejadorAutenticacion.manejarRegistroPorMenu_Paso3(respuesta, salida, this);
                if (exitoRegistro) {
                    manejadorSalas.mostrarMenuSalaPrincipal(this, salida);
                } else {
                    mostrarMenuPrincipal();
                }
                break;
                
            default: 
                salida.writeUTF("Error de estado interno. Volviendo al menú principal.");
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
                      "  3. Entrar como Invitado\n" +
                      "----------------------------------------------------\n" +
                      "Ingresa el número de tu opción (1, 2 o 3):";
        this.salida.writeUTF(menu);
        this.estadoActual = EstadoMenu.MENU_PRINCIPAL;
    }
}