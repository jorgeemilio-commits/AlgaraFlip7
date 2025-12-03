package com.servidormulti;

import java.io.DataOutputStream;
import java.io.IOException;
import java.util.List;

/**
 * Maneja el menú de salas, la creación, unión y el intercambio de mensajes
 * cuando el usuario está dentro de una sala.
 */

public class ManejadorSalas {

    private final GrupoDB grupoDB;
    private final ManejadorMensajes manejadorMensajes;

    public ManejadorSalas(GrupoDB grupoDB, ManejadorMensajes manejadorMensajes) {
        this.grupoDB = grupoDB;
        this.manejadorMensajes = manejadorMensajes;
    }

    /**
     * Método principal que procesa la entrada del usuario según el estado de sala en el que se encuentre.
     * * @param mensaje Lo que escribió el usuario.
     * @param cliente El objeto del cliente que envía el mensaje.
     * @param salida  El flujo de salida para responderle al cliente.
     */

    public void procesar(String mensaje, UnCliente cliente, DataOutputStream salida) throws IOException {
        switch (cliente.obtenerEstadoActual()) {
            
            // --- ESTADO: Menú Principal de Salas (Opciones 1, 2, 3) ---
            case MENU_SALA_PRINCIPAL:
                if (mensaje.equals("1")) { // Opción: Unirse a Sala
                    mostrarSalasDisponibles(cliente, salida);
                    
                } else if (mensaje.equals("2")) { // Opción: Crear Sala
                    if (!cliente.estaLogueado()) {
                        // Invitados no pueden crear salas
                        salida.writeUTF("Error: Solo los usuarios registrados pueden crear salas.");
                        mostrarMenuSalaPrincipal(cliente, salida);
                    } else {
                        // Pedimos el nombre de la nueva sala
                        cliente.establecerEstadoActual(EstadoMenu.MENU_CREAR_SALA_NOMBRE);
                        salida.writeUTF("Introduce el nombre de la nueva sala (solo letras/números), Usa /salir para cancelar.");
                    }
                    
                } else if (mensaje.equals("3")) { // Opción: Logout
                    // Ejecutamos logout en el cliente y lo mandamos al inicio
                    cliente.manejarLogout(); 
                    cliente.mostrarMenuPrincipal();
                    
                } else {
                    salida.writeUTF("Opción no válida. Por favor, ingresa 1, 2, o 3.");
                    mostrarMenuSalaPrincipal(cliente, salida);
                }
                break;

            // --- ESTADO: Eligiendo sala de la lista (entrada numérica) ---
            case MENU_UNIRSE_SALA:
                if (mensaje.trim().equalsIgnoreCase("/salir")) {
                    mostrarMenuSalaPrincipal(cliente, salida);
                    return;
                }

                try {
                    int indiceSeleccionado = Integer.parseInt(mensaje.trim());
                    // Obtenemos la lista ordenada que se le mostró al usuario
                    List<String> salas = grupoDB.obtenerNombresDeGrupos(); 

                    if (indiceSeleccionado >= 1 && indiceSeleccionado <= salas.size()) {
                        // Convertimos el número (1-based) a índice de lista (0-based)
                        String nombreSala = salas.get(indiceSeleccionado - 1); 
                        
                        if (unirseASala(nombreSala, cliente, salida)) {
                            mostrarInterfazSalaActiva(cliente, salida);
                        } else {
                            mostrarMenuSalaPrincipal(cliente, salida);
                        }
                    } else {
                        salida.writeUTF("Número de sala no válido. Elige un número de la lista o escribe /salir.");
                        mostrarSalasDisponibles(cliente, salida); // Volvemos a mostrar la lista
                    }
                    
                } catch (NumberFormatException e) {
                    salida.writeUTF("Entrada no válida. Debes escribir el NÚMERO de la sala.");
                    mostrarSalasDisponibles(cliente, salida);
                }
                break;

            // --- ESTADO: Escribiendo nombre para crear nueva sala ---
            case MENU_CREAR_SALA_NOMBRE:
                if (crearSala(mensaje, cliente, salida)) {
                    mostrarInterfazSalaActiva(cliente, salida);
                } else {
                    mostrarMenuSalaPrincipal(cliente, salida);
                }
                break;

            // --- ESTADO: Chateando dentro de una sala ---
            case SALA_ACTIVA:
                // Comando especial para salir al menú de salas
                if (mensaje.trim().equalsIgnoreCase("/salir")) {
                    salirDelGrupoActual(cliente);
                    mostrarMenuSalaPrincipal(cliente, salida);
                } else {
                    // Si no es comando, es un mensaje de chat normal
                    String nombreSala = cliente.obtenerSalaActual();
                    if (nombreSala != null) {
                        // Formateamos el mensaje para que el ManejadorMensajes sepa a qué grupo va (#Sala Mensaje)
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

    /**
     * Saca al usuario de la sala actual en la base de datos y limpia su estado.
     */
    public void salirDelGrupoActual(UnCliente cliente) {
        String sala = cliente.obtenerSalaActual();
        if (sala != null) {
            grupoDB.salirGrupo(sala, cliente.getNombreUsuario());
            cliente.establecerSalaActual(null);
        }
    }

    /**
     * Lógica para unir un usuario a una sala por nombre.
     */
    private boolean unirseASala(String nombreSala, UnCliente cliente, DataOutputStream salida) throws IOException {
        String resultado = grupoDB.unirseGrupo(nombreSala, cliente.getNombreUsuario());
        
        if (resultado.contains("Error") || resultado.contains("no existe")) {
            salida.writeUTF(resultado + " Intenta de nuevo.");
            return false;
        }
        
        // Marcamos en el cliente que ahora está en esta sala
        cliente.establecerSalaActual(nombreSala);
        salida.writeUTF(resultado + " ¡Has entrado a la sala!");
        return true;
    }

    /**
     * Lógica interna para crear una sala y unirse automáticamente.
     */
    private boolean crearSala(String nombreSala, UnCliente cliente, DataOutputStream salida) throws IOException {
        // Validación: solo numeros y letras
        if (!nombreSala.matches("[a-zA-Z0-9]+")) {
            salida.writeUTF("Error: El nombre de la sala solo puede contener letras y números.");
            return false;
        }

        String resultadoCreacion = grupoDB.crearGrupo(nombreSala);

        if (resultadoCreacion.contains("Error") || resultadoCreacion.contains("ya existe")) {
            salida.writeUTF(resultadoCreacion + " Intenta de nuevo.");
            return false;
        }

        // Si se crea con éxito, nos unimos automáticamente
        if (unirseASala(nombreSala, cliente, salida)) {
            salida.writeUTF(resultadoCreacion + " Te has unido automáticamente.");
            return true;
        } else {
            salida.writeUTF("Error: Sala creada, pero no se pudo unir automáticamente.");
            return false;
        }
    }

    // --- INTERFAZ ---

    /**
     * Muestra el menú donde el usuario elige entre Unirse, Crear o Logout.
     */
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

    /**
     * Muestra la lista numerada de salas disponibles obtenida de la base de datos.
     */
    public void mostrarSalasDisponibles(UnCliente cliente, DataOutputStream salida) throws IOException {
        List<String> salas = grupoDB.obtenerNombresDeGrupos();
        
        StringBuilder lista = new StringBuilder("\n--- SALAS DISPONIBLES ---\n");
        if (salas.isEmpty()) {
            lista.append("No hay salas disponibles. ¡Crea una con la opción 2!\n");
        } else {
            int contador = 1;
            for (String sala : salas) {
                // Formato: "1. NombreSala"
                lista.append(contador).append(". ").append(sala).append("\n");
                contador++;
            }
        }
        lista.append("------------------------------------------\n");
        lista.append("Escribe el NÚMERO de la sala para unirte (o /salir para volver):");
        
        salida.writeUTF(lista.toString());
        // Cambiamos estado para esperar el número de sala
        cliente.establecerEstadoActual(EstadoMenu.MENU_UNIRSE_SALA);
    }

    /**
     * Muestra el mensaje de bienvenida a la sala de chat activa.
     */
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