package com.servidormulti;

import java.io.DataOutputStream;
import java.io.IOException;

public class ManejadorMenu {

    private final ManejadorAutenticacion manejadorAutenticacion;
    private final ManejadorSalas manejadorSalas;

    public ManejadorMenu(ManejadorAutenticacion auth, ManejadorSalas salas) {
        this.manejadorAutenticacion = auth;
        this.manejadorSalas = salas;
    }

    // Verifica si el estado actual es uno relacionado con salas
    public boolean esEstadoDeSalas(EstadoMenu estado) {
        return estado == EstadoMenu.MENU_SALA_PRINCIPAL ||
               estado == EstadoMenu.MENU_UNIRSE_SALA ||
               estado == EstadoMenu.MENU_CREAR_SALA_NOMBRE ||
               estado == EstadoMenu.SALA_ACTIVA;
    }

    // Muestra el menú principal al cliente
    public void mostrarMenuPrincipal(UnCliente cliente, DataOutputStream salida) throws IOException {
        String menu = "\n" +
                      "--- Bienvenido al Servidor de Chat ---\n" +
                      "Selecciona una opción:\n" +
                      "  1. Iniciar Sesión\n" +
                      "  2. Registrar Nueva Cuenta\n" +
                      "----------------------------------------------------\n" +
                      "Ingresa el número de tu opción (1 o 2):";
        salida.writeUTF(menu);
        cliente.establecerEstadoActual(EstadoMenu.MENU_PRINCIPAL);
    }

    // Procesa la lógica del menú principal y autenticación
    public void procesar(String respuesta, UnCliente cliente, DataOutputStream salida) throws IOException {
        
        switch (cliente.obtenerEstadoActual()) {
            
            case MENU_PRINCIPAL:
                if (respuesta.equals("1")) { 
                    cliente.establecerEstadoActual(EstadoMenu.INICIO_SESION_PEDIR_NOMBRE); 
                    salida.writeUTF("Introduce tu nombre de usuario:");
                } else if (respuesta.equals("2")) { 
                    cliente.establecerEstadoActual(EstadoMenu.REGISTRO_PEDIR_NOMBRE); 
                    salida.writeUTF("Introduce tu nombre de usuario (solo letras/números/acentos):");
                } else {
                    salida.writeUTF("Opción no válida. Por favor, ingresa 1 o 2.");
                    mostrarMenuPrincipal(cliente, salida);
                }
                break;
                
            case INICIO_SESION_PEDIR_NOMBRE:
                cliente.establecerNombreTemporal(respuesta);
                cliente.establecerEstadoActual(EstadoMenu.INICIO_SESION_PEDIR_CONTRASENA); 
                salida.writeUTF("Introduce tu contraseña:");
                break;
                
            case INICIO_SESION_PEDIR_CONTRASENA:
                boolean exitoLogin = manejadorAutenticacion.manejarInicioSesionInternoPorMenu(
                    cliente.obtenerNombreTemporal(), 
                    respuesta, 
                    salida, 
                    cliente
                );
                
                cliente.limpiarTemporales(); 
                
                if (exitoLogin) {
                    manejadorSalas.mostrarMenuSalaPrincipal(cliente, salida);
                } else {
                    mostrarMenuPrincipal(cliente, salida);
                }
                break;
                
            case REGISTRO_PEDIR_NOMBRE:
                manejadorAutenticacion.manejarRegistroPorMenu_Paso1(respuesta, salida, cliente);
                break;
                
            case REGISTRO_PEDIR_CONTRASENA:
                manejadorAutenticacion.manejarRegistroPorMenu_Paso2(respuesta, salida, cliente);
                break;
                
            case REGISTRO_PEDIR_CONFIRMACION:
                boolean exitoRegistro = manejadorAutenticacion.manejarRegistroPorMenu_Paso3(respuesta, salida, cliente);
                if (exitoRegistro) {
                    manejadorSalas.mostrarMenuSalaPrincipal(cliente, salida); 
                } else {
                    mostrarMenuPrincipal(cliente, salida); 
                }
                break;
                
            default: 
                salida.writeUTF("Error de estado interno. Volviendo al menú principal.");
                mostrarMenuPrincipal(cliente, salida);
        }
    }
}