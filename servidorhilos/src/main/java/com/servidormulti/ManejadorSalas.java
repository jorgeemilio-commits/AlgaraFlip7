package com.servidormulti;

import java.io.DataOutputStream;
import java.io.IOException;
import java.util.List;

public class ManejadorSalas {

    private final GrupoDB grupoDB;
    private final ManejadorMensajes manejadorMensajes;

    public ManejadorSalas(GrupoDB grupoDB, ManejadorMensajes manejadorMensajes) {
        this.grupoDB = grupoDB;
        this.manejadorMensajes = manejadorMensajes;
    }

    public void procesar(String mensaje, UnCliente cliente, DataOutputStream salida) throws IOException {
        switch (cliente.obtenerEstadoActual()) {
            
            case MENU_SALA_PRINCIPAL:
                if (mensaje.equals("1")) { // Unirse a una sala
                    mostrarSalasDisponibles(cliente, salida);
                    
                } else if (mensaje.equals("2")) { // Crear una sala
                    if (!cliente.estaLogueado()) { // Solo usuarios registrados pueden crear salas
                        salida.writeUTF("Error: Solo los usuarios registrados pueden crear salas.");
                        mostrarMenuSalaPrincipal(cliente, salida);
                    } else {
                        cliente.establecerEstadoActual(EstadoMenu.MENU_CREAR_SALA_NOMBRE); // Esperando nombre de sala
                        salida.writeUTF("Introduce el nombre de la nueva sala (solo letras/números):");
                    }
                    
                } else if (mensaje.equals("3")) { // Cerrar sesión
                    cliente.manejarLogout(); 
                    cliente.mostrarMenuPrincipal();
                    
                } else {
                    salida.writeUTF("Opción no válida. Por favor, ingresa 1, 2, o 3.");
                    mostrarMenuSalaPrincipal(cliente, salida);
                }
                break;

            case MENU_UNIRSE_SALA:
                if (mensaje.trim().equalsIgnoreCase("/salir")) { // Volver al menú principal de salas
                    mostrarMenuSalaPrincipal(cliente, salida);
                    return;
                }
                
                try { // Consigue una lista de las salas disponibles
                    int indiceSeleccionado = Integer.parseInt(mensaje.trim());
                    List<String> salas = grupoDB.obtenerNombresDeGrupos(); 
                    
                    if (indiceSeleccionado >= 1 && indiceSeleccionado <= salas.size()) {
                        String nombreSala = salas.get(indiceSeleccionado - 1); 
                        
                        // Intenta unirse a la sala seleccionada
                        if (unirseASala(nombreSala, cliente, salida)) {
                            mostrarInterfazSalaActiva(cliente, salida);
                        } else {
                            mostrarMenuSalaPrincipal(cliente, salida);
                        }
                    } else { // Índice fuera de rango
                        salida.writeUTF("Número de sala no válido. Elige un número de la lista o escribe /salir.");
                        mostrarSalasDisponibles(cliente, salida);
                    }
                    
                } catch (NumberFormatException e) { // Entrada no es un número válido
                    salida.writeUTF("Entrada no válida. Debes escribir el NÚMERO de la sala.");
                    mostrarSalasDisponibles(cliente, salida);
                }
                break;

            case MENU_CREAR_SALA_NOMBRE:
                if (crearSala(mensaje, cliente, salida)) { // Intenta crear la sala
                    mostrarInterfazSalaActiva(cliente, salida);
                } else {
                    mostrarMenuSalaPrincipal(cliente, salida);
                }
                break;

            case SALA_ACTIVA:
                if (mensaje.trim().equalsIgnoreCase("/salir")) { // Salir de la sala y volver al menú principal de salas
                    salirDelGrupoActual(cliente);
                    mostrarMenuSalaPrincipal(cliente, salida);
                } else {
                    String nombreSala = cliente.obtenerSalaActual();
                    if (nombreSala != null) {  // Enviar mensaje a la sala actual
                        String mensajeSala = "#" + nombreSala + " " + mensaje;
                        manejadorMensajes.enrutarMensaje(cliente, mensajeSala);
                    } else {
                        salida.writeUTF("Error: No estás en una sala válida.");
                        mostrarMenuSalaPrincipal(cliente, salida);
                    }
                }
                break;

            default:
                salida.writeUTF("Error de estado de salas.");
                mostrarMenuSalaPrincipal(cliente, salida);
                break;
        }
    }

    public void salirDelGrupoActual(UnCliente cliente) { // Sale del grupo actual si está en uno
        String sala = cliente.obtenerSalaActual();
        if (sala != null) {
            grupoDB.salirGrupo(sala, cliente.getNombreUsuario());
            cliente.establecerSalaActual(null);
        }
    }

    private boolean unirseASala(String nombreSala, UnCliente cliente, DataOutputStream salida) throws IOException {
        // Intenta unirse a la sala
        String resultado = grupoDB.unirseGrupo(nombreSala, cliente.getNombreUsuario());
        
        if (resultado.contains("Error") || resultado.contains("no existe")) {
            salida.writeUTF(resultado + " Intenta de nuevo.");
            return false;
        }
        
        // Actualiza la sala actual del cliente
        cliente.establecerSalaActual(nombreSala);
        salida.writeUTF(resultado + " ¡Has entrado a la sala!");
        return true;
    }

    private boolean crearSala(String nombreSala, UnCliente cliente, DataOutputStream salida) throws IOException {
        // Valida el nombre de la sala
        if (!nombreSala.matches("[a-zA-Z0-9]+")) {
            salida.writeUTF("Error: El nombre de la sala solo puede contener letras y números.");
            return false;
        }

        // Intenta crear la sala
        String resultadoCreacion = grupoDB.crearGrupo(nombreSala);

        // Checa si hubo un error al crear la sala
        if (resultadoCreacion.contains("Error") || resultadoCreacion.contains("ya existe")) {
            salida.writeUTF(resultadoCreacion + " Intenta de nuevo, hubo un error o el nombre ya es usado.");
            return false;
        }

        // Se une automáticamente a la sala creada
        if (unirseASala(nombreSala, cliente, salida)) {
            salida.writeUTF(resultadoCreacion + " Te has unido automáticamente.");
            return true;
        } else {
            salida.writeUTF("Error: Sala creada, pero no se pudo unir automáticamente.");
            return false;
        }
    }

    // Muestra el menú principal de salas al cliente
    public void mostrarMenuSalaPrincipal(UnCliente cliente, DataOutputStream salida) throws IOException {
        String estado = cliente.estaLogueado() ? "Logueado" : "Invitado (Solo chat)";
        String mensajeInvitado = cliente.estaLogueado() ? "" : " (No puedes crear salas)";
        
        String menu = "\n" +
                      "--- MENÚ PRINCIPAL DE SALAS - " + estado + " como: " + cliente.getNombreUsuario() + mensajeInvitado + " ---\n" +
                      "Selecciona una opción:\n" +
                      "  1. Unirse a una Sala (Entrar a chatear)\n" +
                      "  2. Crear una Sala\n" +
                      "  3. Cerrar Sesión\n" +
                      "----------------------------------------------------\n" +
                      "Ingresa el número de tu opción (1, 2 o 3):";
        salida.writeUTF(menu);
        cliente.establecerEstadoActual(EstadoMenu.MENU_SALA_PRINCIPAL);
    }

    // Muestra la lista de salas disponibles para unirse
    public void mostrarSalasDisponibles(UnCliente cliente, DataOutputStream salida) throws IOException {
        List<String> salas = grupoDB.obtenerNombresDeGrupos();
        
        StringBuilder lista = new StringBuilder("\n--- SALAS DISPONIBLES ---\n");
        if (salas.isEmpty()) {
            lista.append("No hay salas disponibles. ¡Crea una con la opción 2!\n");
        } else {
            int contador = 1;
            for (String sala : salas) {
                lista.append(contador).append(". ").append(sala).append("\n");
                contador++;
            }
        }
        lista.append("------------------------------------------\n");
        lista.append("Escribe el NÚMERO de la sala para unirte (o /salir para volver):");
        
        salida.writeUTF(lista.toString());
        cliente.establecerEstadoActual(EstadoMenu.MENU_UNIRSE_SALA);
    }

    // Muestra la interfaz de chat activa en la sala
    public void mostrarInterfazSalaActiva(UnCliente cliente, DataOutputStream salida) throws IOException {
        String limite = "Sin límite de mensajes."; 
        
        String menu = "\n" +
                      "--- SALA ACTIVA: #" + cliente.obtenerSalaActual() + " ---\n" +
                      "  * Escribe tu mensaje y presiona Enter.\n" +
                      "  * Para volver al menú principal de salas: /salir\n" +
                      "  " + limite + "\n" +
                      "----------------------------------------------------\n" +
                      "¡Comienza a chatear!";
        salida.writeUTF(menu);
        cliente.establecerEstadoActual(EstadoMenu.SALA_ACTIVA);
    }
}