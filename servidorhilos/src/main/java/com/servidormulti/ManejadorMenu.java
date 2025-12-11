package com.servidormulti;

import java.io.DataOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.Map;

public class ManejadorMenu {

    private final ManejadorAutenticacion manejadorAutenticacion;
    private final ManejadorSalas manejadorSalas;

    public ManejadorMenu(ManejadorAutenticacion auth, ManejadorSalas salas) {
        this.manejadorAutenticacion = auth;
        this.manejadorSalas = salas;
    }

    // --- Lógica de Estado ---
    public boolean esEstadoDeSalas(EstadoMenu estado) {
        return estado == EstadoMenu.MENU_SALA_PRINCIPAL ||
               estado == EstadoMenu.MENU_UNIRSE_SALA ||
               estado == EstadoMenu.MENU_CREAR_SALA_NOMBRE ||
               estado == EstadoMenu.SALA_ACTIVA;
    }

    // --- VISTAS ---

    public void mostrarMenuPrincipal(UnCliente cliente, DataOutputStream salida) throws IOException {
        String menu = "\n" +
                      "--- Bienvenido al Servidor de Chat ---\n" +
                      "Selecciona una opción:\n" +
                      "  1. Iniciar Sesión (Usuario registrado)\n" +
                      "  2. Registrar Nueva Cuenta\n" +
                      "----------------------------------------------------\n" +
                      "Ingresa el número de tu opción (1 o 2):";
        salida.writeUTF(menu);
        cliente.establecerEstadoActual(EstadoMenu.MENU_PRINCIPAL);
    }

    public void mostrarMenuSalaPrincipal(UnCliente cliente, DataOutputStream salida) throws IOException {
        String estado = cliente.estaLogueado() ? "Logueado" : "Invitado (Solo chat)";
        String mensajeInvitado = cliente.estaLogueado() ? "" : " (No puedes crear salas)";
        
        String menu = "\n" +
                      "--- MENU PRINCIPAL DE SALAS - " + estado + " como: " + cliente.getNombreUsuario() + mensajeInvitado + " ---\n" +
                      "Selecciona una opción:\n" +
                      "  1. Unirse a una Sala \n" +
                      "  2. Crear una Sala\n" +
                      "  3. Cerrar Sesión\n" +
                      "----------------------------------------------------\n" +
                      "Ingresa el número de tu opción (1, 2 o 3):";
        salida.writeUTF(menu);
        cliente.establecerEstadoActual(EstadoMenu.MENU_SALA_PRINCIPAL);
    }

    public void mostrarSalasDisponibles(UnCliente cliente, DataOutputStream salida, Map<String, Integer> salas) throws IOException {
        StringBuilder lista = new StringBuilder("\n--- SALAS DISPONIBLES (Máx 6) ---\n");
        
        if (salas.isEmpty()) {
            lista.append("No hay salas disponibles. ¡Crea una con la opción 2!\n");
        } else {
            int contador = 1;
            for (Map.Entry<String, Integer> entry : salas.entrySet()) {
                String nombreSala = entry.getKey();
                int cantidadJugadores = entry.getValue();
                
                lista.append(contador)
                     .append(". ")
                     .append(nombreSala)
                     .append(" (")
                     .append(cantidadJugadores)
                     .append("/6)\n");
                contador++;
            }
        }
        lista.append("------------------------------------------\n");
        lista.append("Escribe el NÚMERO de la sala para unirte (o /salir para volver):");
        
        salida.writeUTF(lista.toString());
        cliente.establecerEstadoActual(EstadoMenu.MENU_UNIRSE_SALA);
    }

    public void mostrarInterfazSalaActiva(UnCliente cliente, DataOutputStream salida, String nombreSala) throws IOException {
        String limite = "Sin límite de mensajes."; 
        
        String menu = "\n" +
                      "--- SALA ACTIVA: #" + nombreSala + " ---\n" +
                      "  * Escribe tu mensaje y presiona Enter.\n" +
                      "  * Escribe /jugadores para ver quién está en la sala.\n" +
                      "  * Escribe /listo para votar iniciar partida (min 3).\n" +
                      "  * Escribe /nolisto para cancelar tu voto.\n" +
                      "  * Para volver al menú principal de salas: /salir\n" +
                      "  " + limite + "\n" +
                      "----------------------------------------------------\n" +
                      "¡Comienza a chatear!";
        salida.writeUTF(menu);
        cliente.establecerEstadoActual(EstadoMenu.SALA_ACTIVA);
    }

    public void mostrarJugadoresEnSala(UnCliente cliente, DataOutputStream salida, List<String> nombresMiembros, String nombreSala) throws IOException {
        StringBuilder mensaje = new StringBuilder("\n--- JUGADORES EN LA SALA: " + nombreSala + " ---\n");
        
        if (nombresMiembros.isEmpty()) {
            mensaje.append("No hay jugadores en esta sala (extraño...).\n");
        } else {
            mensaje.append("Total: ").append(nombresMiembros.size()).append("/6\n");
            mensaje.append("--------------------\n");
            
            int contador = 1;
            for (String nombre : nombresMiembros) {
                String marcador = nombre.equals(cliente.getNombreUsuario()) ? " (TÚ)" : "";
                mensaje.append(contador).append(". ")
                       .append(nombre)
                       .append(marcador)
                       .append("\n");
                contador++;
            }
        }
        mensaje.append("------------------------------------\n");
        salida.writeUTF(mensaje.toString());
    }

    // --- PROCESAMIENTO (Menú Principal / Auth) ---
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
                    cliente.obtenerNombreTemporal(), respuesta, salida, cliente
                );
                cliente.limpiarTemporales(); 
                
                if (exitoLogin) {
                    mostrarMenuSalaPrincipal(cliente, salida); // Ahora llamamos al método local
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
                    mostrarMenuSalaPrincipal(cliente, salida); // Ahora llamamos al método local
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