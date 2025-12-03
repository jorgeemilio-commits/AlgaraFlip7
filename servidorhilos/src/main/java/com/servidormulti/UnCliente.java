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
import com.servidormulti.ManejadorSalas; 

/**
 * Clase que representa un hilo de ejecución para un cliente conectado.
 * Se encarga de:
 * 1. Gestionar la conexión de red (Sockets).
 * 2. Almacenar el estado de la sesión (Usuario, Login, Sala actual).
 * 3. Enrutar los mensajes entrantes hacia la Autenticación o hacia las Salas.
 */
public class UnCliente implements Runnable {
    
    final DataOutputStream salida;
    final DataInputStream entrada;

    final String clienteID; 
    private String nombreUsuario; 

    private final ManejadorAutenticacion manejadorAutenticacion;
    private final ManejadorSalas manejadorSalas; 
    private boolean logueado = false;

    // --- ESTADOS ---

    private EstadoMenu estadoActual = EstadoMenu.MENU_PRINCIPAL; 
    
    private String nombreTemporal = null;
    private String contrasenaTemporal = null;
    
    private String salaActual = null;

    private final PrintWriter salidaUTF;
    private final BufferedReader entradaUTF;
    
    UnCliente(Socket s, String id, ContextoServidor contexto) throws java.io.IOException {
        this.salidaUTF = new PrintWriter(new OutputStreamWriter(s.getOutputStream(), "UTF-8"), true);
        this.entradaUTF = new BufferedReader(new InputStreamReader(s.getInputStream(), "UTF-8"));
        
        // Asignamos ID y un nombre por defecto (Invitado-X)
        this.clienteID = id;
        this.nombreUsuario = "Invitado-" + id;

        this.salida = new DataOutputStream(s.getOutputStream());
        this.entrada = new DataInputStream(s.getInputStream());

        // Obtenemos las instancias compartidas de los manejadores
        this.manejadorAutenticacion = contexto.getManejadorAutenticacion();
        this.manejadorSalas = contexto.getManejadorSalas(); 
    }

    // --- GETTERS Y SETTERS ---

    public synchronized EstadoMenu obtenerEstadoActual() { return estadoActual; }
    public synchronized void establecerEstadoActual(EstadoMenu estado) { this.estadoActual = estado; }
    
    public synchronized String obtenerNombreTemporal() { return nombreTemporal; }
    public synchronized void establecerNombreTemporal(String nombre) { this.nombreTemporal = nombre; }
    
    public synchronized String obtenerContrasenaTemporal() { return contrasenaTemporal; }
    public synchronized void establecerContrasenaTemporal(String password) { this.contrasenaTemporal = password; }
    
    /**
     * Limpia los datos temporales después de un intento de login o registro.
     */
    public synchronized void limpiarTemporales() { 
        this.nombreTemporal = null; 
        this.contrasenaTemporal = null;
    }
    
    public synchronized String obtenerSalaActual() { return salaActual; }
    public synchronized void establecerSalaActual(String sala) { this.salaActual = sala; }

    
    public String getNombreUsuario() { return nombreUsuario; }
    public boolean estaLogueado() { return logueado; }
    
    /**
     * Finaliza el proceso de login exitoso.
     * Actualiza el nombre real y marca al usuario como logueado.
     */

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
    
    /**
     * Realiza la limpieza interna al cerrar sesión.
     * Si estaba en una sala, lo saca de ella. Resetea el nombre a Invitado.
     */

    public void manejarLogoutInterno() {
        if (this.logueado) {
            // Avisar al manejador de salas que este usuario sale
            manejadorSalas.salirDelGrupoActual(this);
            
            this.logueado = false;
            this.nombreUsuario = "Invitado-" + this.clienteID; 
        }
    }
    
    /**
     * Método para ejecutar logout y notificar al usuario.
     */

    public void manejarLogout() throws IOException {
        manejarLogoutInterno();
        this.salida.writeUTF("Has cerrado sesión. Tu nombre es ahora '" + this.getNombreUsuario() + "'.");
    }

    /**
     * Bucle principal del hilo.
     * Escucha los mensajes del cliente y decide quién debe procesarlos.
     */

    @Override
    public void run() {
        try {
            // Al conectar, mostramos el menú principal
            mostrarMenuPrincipal();
        } catch (IOException e) { System.err.println("Error de bienvenida: " + e.getMessage()); }

        while (true) {
            try {
                // Leemos lo que escribe el usuario
                String mensaje = entrada.readUTF();

                // DECISIÓN DE ENRUTAMIENTO:
                
                // 1. Si el estado actual pertenece a la lógica de SALAS (menú de salas, chat, unirse)...
                if (esEstadoDeSalas(estadoActual)) {
                    // Delegamos la tarea al ManejadorSalas
                    manejadorSalas.procesar(mensaje, this, salida);
                } 
                // 2. Si no, significa que estamos en el MENÚ PRINCIPAL o en AUTENTICACIÓN (Login/Registro)
                else {
                    // Lo procesamos aquí
                    procesarAuth(mensaje, salida);
                }
                
            } catch (Exception ex) {
                // Manejo de desconexión repentina
                System.out.println("Cliente " + this.nombreUsuario + " se ha desconectado.");
                
                // Aseguramos que salga de la sala si estaba en una
                if (this.salaActual != null) {
                    manejadorSalas.salirDelGrupoActual(this);
                }

                // Eliminamos de la lista global y cerramos sockets
                ServidorMulti.clientes.remove(this.clienteID); 
                try {
                    this.entrada.close();
                    this.salida.close();
                } catch (IOException e) { e.printStackTrace(); }
                break;
            }
        }
    }
    
    /**
     * Determinar si un estado pertenece al flujo de Salas.
     */

    private boolean esEstadoDeSalas(EstadoMenu estado) {
        return estado == EstadoMenu.MENU_SALA_PRINCIPAL ||
               estado == EstadoMenu.MENU_UNIRSE_SALA ||
               estado == EstadoMenu.MENU_CREAR_SALA_NOMBRE ||
               estado == EstadoMenu.SALA_ACTIVA;
    }

    /**
     * Procesa las opciones del Menú Principal (1. Login, 2. Registro, 3. Invitado)
     * y los pasos de autenticación (pedir usuario, pedir contraseña).
     */

    public void procesarAuth(String respuesta, DataOutputStream salida) throws IOException {
        
        switch (obtenerEstadoActual()) {
            
            case MENU_PRINCIPAL:
                if (respuesta.equals("1")) { // Opción: Iniciar Sesión
                    establecerEstadoActual(EstadoMenu.INICIO_SESION_PEDIR_NOMBRE);
                    salida.writeUTF("Introduce tu nombre de usuario:");
                    
                } else if (respuesta.equals("2")) { // Opción: Registrar
                    establecerEstadoActual(EstadoMenu.REGISTRO_PEDIR_NOMBRE);
                    salida.writeUTF("Introduce tu nombre de usuario (solo letras/números/acentos):");
                    
                } else if (respuesta.equals("3")) { // Opción: Invitado
                    salida.writeUTF("Has entrado como Invitado.");
                    // Redirigimos al usuario al Menú de Salas
                    manejadorSalas.mostrarMenuSalaPrincipal(this, salida);
                    
                } else {
                    salida.writeUTF("Opción no válida. Por favor, ingresa 1, 2, o 3.");
                    mostrarMenuPrincipal();
                }
                break;
                
            // --- FLUJO DE LOGIN ---
            case INICIO_SESION_PEDIR_NOMBRE:
                establecerNombreTemporal(respuesta); // Guardamos nombre
                establecerEstadoActual(EstadoMenu.INICIO_SESION_PEDIR_CONTRASENA);
                salida.writeUTF("Introduce tu contraseña:");
                break;
                
            case INICIO_SESION_PEDIR_CONTRASENA:
                // ManejadorAutenticacion verifica las credenciales
                boolean exitoLogin = manejadorAutenticacion.manejarInicioSesionInternoPorMenu(
                    obtenerNombreTemporal(), 
                    respuesta, // Esta es la contraseña
                    salida, 
                    this
                );
                
                limpiarTemporales(); 
                
                if (exitoLogin) {
                    // Si login ok, vamos a salas
                    manejadorSalas.mostrarMenuSalaPrincipal(this, salida);
                } else {
                    // Si falla, volvemos al inicio
                    mostrarMenuPrincipal(); 
                }
                break;
                
            // --- FLUJO DE REGISTRO ---
            case REGISTRO_PEDIR_NOMBRE:
                manejadorAutenticacion.manejarRegistroPorMenu_Paso1(respuesta, salida, this);
                break;
                
            case REGISTRO_PEDIR_CONTRASENA:
                manejadorAutenticacion.manejarRegistroPorMenu_Paso2(respuesta, salida, this);
                break;
                
            case REGISTRO_PEDIR_CONFIRMACION:
                boolean exitoRegistro = manejadorAutenticacion.manejarRegistroPorMenu_Paso3(respuesta, salida, this);
                if (exitoRegistro) {
                    // Si registro y autologin ok, vamos a salas
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

    /**
     * Muestra el texto del Menú Principal inicial (Login/Registro).
     */
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