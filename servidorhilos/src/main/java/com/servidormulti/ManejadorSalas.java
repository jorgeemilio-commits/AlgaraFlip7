package com.servidormulti;

import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList; 
import java.util.HashMap;
import java.util.HashSet; 
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.servidormulti.Flip7.SesionJuego;

public class ManejadorSalas {

    private final GrupoDB grupoDB;
    private final ManejadorMensajes manejadorMensajes;
    
    // mapas para controlar las partidas y los votos de listo
    private final Map<String, SesionJuego> partidasActivas;
    private final Map<String, Set<String>> votosListo;

    public ManejadorSalas(GrupoDB grupoDB, ManejadorMensajes manejadorMensajes) {
        this.grupoDB = grupoDB;
        this.manejadorMensajes = manejadorMensajes;
        this.partidasActivas = new HashMap<>();
        this.votosListo = new HashMap<>();
    }

    public void procesar(String mensaje, UnCliente cliente, DataOutputStream salida) throws IOException {
        switch (cliente.obtenerEstadoActual()) {
            
            case MENU_SALA_PRINCIPAL:
                if (mensaje.equals("1")) { // Unirse a una sala
                    mostrarSalasDisponibles(cliente, salida);
                    
                } else if (mensaje.equals("2")) { // Crear una sala
                    if (!cliente.estaLogueado()) { 
                        salida.writeUTF("Error: Solo los usuarios registrados pueden crear salas.");
                        mostrarMenuSalaPrincipal(cliente, salida);
                    } else {
                        cliente.establecerEstadoActual(EstadoMenu.MENU_CREAR_SALA_NOMBRE); 
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
                if (mensaje.trim().equalsIgnoreCase("/salir")) { 
                    mostrarMenuSalaPrincipal(cliente, salida);
                    return;
                }
                
                try { 
                    // Obtenemos el mapa actualizado para validar la selección
                    Map<String, Integer> salasMap = grupoDB.obtenerSalasDisponibles();
                    List<String> salasLista = new ArrayList<>(salasMap.keySet());
                    
                    int indiceSeleccionado = Integer.parseInt(mensaje.trim());
                    
                    if (indiceSeleccionado >= 1 && indiceSeleccionado <= salasLista.size()) {
                        String nombreSala = salasLista.get(indiceSeleccionado - 1); 
                        
                        // Intenta unirse a la sala seleccionada
                        if (unirseASala(nombreSala, cliente, salida)) {
                            mostrarInterfazSalaActiva(cliente, salida);
                        } else {
                            mostrarMenuSalaPrincipal(cliente, salida);
                        }
                    } else { 
                        salida.writeUTF("Número de sala no válido. Elige un número de la lista o escribe /salir.");
                        mostrarSalasDisponibles(cliente, salida);
                    }
                    
                } catch (NumberFormatException e) { 
                    salida.writeUTF("Entrada no válida. Debes escribir el NÚMERO de la sala.");
                    mostrarSalasDisponibles(cliente, salida);
                }
                break;

            case MENU_CREAR_SALA_NOMBRE:
                if (crearSala(mensaje, cliente, salida)) { 
                    mostrarInterfazSalaActiva(cliente, salida);
                } else {
                    mostrarMenuSalaPrincipal(cliente, salida);
                }
                break;

            case SALA_ACTIVA:
                if (mensaje.trim().equalsIgnoreCase("/salir")) { 
                    salirDelGrupoActual(cliente);
                    mostrarMenuSalaPrincipal(cliente, salida);
                } else if (mensaje.trim().equalsIgnoreCase("/jugadores") || mensaje.trim().equalsIgnoreCase("/miembros")) {
                    // Comando para ver jugadores en la sala
                    mostrarJugadoresEnSala(cliente, salida);
                } else {
                    String nombreSala = cliente.obtenerSalaActual();
                    if (nombreSala != null) {  
                        
                        SesionJuego juegoActual = partidasActivas.get(nombreSala);

                        if (juegoActual != null && juegoActual.estaJuegoIniciado()) {
                            juegoActual.procesarMensajeJuego(cliente, mensaje);
                        } else {
                            if (mensaje.trim().equalsIgnoreCase("/listo")) {
                                manejarComandoListo(cliente, nombreSala);
                            } else if (mensaje.trim().equalsIgnoreCase("/nolisto")) {
                                manejarComandoNoListo(cliente, nombreSala);
                            } else {
                                String mensajeSala = "#" + nombreSala + " " + mensaje;
                                manejadorMensajes.enrutarMensaje(cliente, mensajeSala);
                            }
                        }

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

    // Muestra los jugadores que están en la sala actual
    private void mostrarJugadoresEnSala(UnCliente cliente, DataOutputStream salida) throws IOException {
        String nombreSala = cliente.obtenerSalaActual();
        if (nombreSala == null) {
            salida.writeUTF("Error: No estás en una sala.");
            return;
        }

        Integer grupoId = grupoDB.getGrupoId(nombreSala);
        if (grupoId == null) {
            salida.writeUTF("Error: La sala no existe.");
            return;
        }

        List<String> nombresMiembros = grupoDB.getMiembrosGrupo(grupoId);
        
        StringBuilder mensaje = new StringBuilder("\n--- JUGADORES EN LA SALA: " + nombreSala + " ---\n");
        
        if (nombresMiembros.isEmpty()) {
            mensaje.append("No hay jugadores en esta sala.\n");
        } else {
            mensaje.append("Total: ").append(nombresMiembros.size()).append("/6\n");
            mensaje.append("--------------------\n");
            
            int contador = 1;
            for (String nombre : nombresMiembros) {
                // Marcar si es el usuario actual
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

    // maneja el comando /listo del cliente
    private void manejarComandoListo(UnCliente cliente, String nombreSala) throws IOException {
        votosListo.putIfAbsent(nombreSala, new HashSet<>());
        Set<String> listos = votosListo.get(nombreSala);

        if (listos.contains(cliente.getNombreUsuario())) {
            cliente.getSalida().writeUTF("Ya estás marcado como listo. Esperando a los demás...");
            return;
        }
        
        listos.add(cliente.getNombreUsuario());
        
        String msgAviso = "#" + nombreSala + " El jugador " + cliente.getNombreUsuario() + " está LISTO (" + listos.size() + "/3 necesarios).";
        manejadorMensajes.enrutarMensaje(cliente, msgAviso);

        if (listos.size() >= 3) {
            iniciarPartidaEnSala(nombreSala);
        }
    }

    // maneja el comando /nolisto del cliente
    private void manejarComandoNoListo(UnCliente cliente, String nombreSala) throws IOException {
        votosListo.putIfAbsent(nombreSala, new HashSet<>());
        Set<String> listos = votosListo.get(nombreSala);

        if (!listos.contains(cliente.getNombreUsuario())) {
            cliente.getSalida().writeUTF("No estabas marcado como listo.");
            return;
        }
        
        listos.remove(cliente.getNombreUsuario());
        
        String msgAviso = "#" + nombreSala + " El jugador " + cliente.getNombreUsuario() + " ya NO está listo (" + listos.size() + "/3 necesarios).";
        manejadorMensajes.enrutarMensaje(cliente, msgAviso);
        
        cliente.getSalida().writeUTF("Has cancelado tu voto de listo.");
    }

    // inicia la partida en la sala indicada
    private void iniciarPartidaEnSala(String nombreSala) {
        Integer grupoId = grupoDB.getGrupoId(nombreSala);
        if (grupoId == null) return;

        List<String> nombresMiembros = grupoDB.getMiembrosGrupo(grupoId);
        List<UnCliente> jugadoresConectados = new ArrayList<>();

        for (String nombre : nombresMiembros) {
            UnCliente c = ServidorMulti.buscarClientePorNombre(nombre); 
            if (c != null && nombreSala.equals(c.obtenerSalaActual())) {
                jugadoresConectados.add(c);
            }
        }

        SesionJuego nuevaPartida = new SesionJuego(jugadoresConectados);
        partidasActivas.put(nombreSala, nuevaPartida);
        votosListo.get(nombreSala).clear();
        nuevaPartida.iniciarPartida();
    }

    // hace que el cliente salga de su sala actual
    public void salirDelGrupoActual(UnCliente cliente) { 
        String sala = cliente.obtenerSalaActual();
        if (sala != null) {
            grupoDB.salirGrupo(sala, cliente.getNombreUsuario());
            
            if (votosListo.containsKey(sala)) {
                votosListo.get(sala).remove(cliente.getNombreUsuario());
            }

            // Si hay una partida activa, remover al jugador
            SesionJuego juego = partidasActivas.get(sala);
            if (juego != null) {
               juego.removerJugador(cliente);
            }

        cliente.establecerSalaActual(null);
        System.out.println("Jugador " + cliente.getNombreUsuario() + " removido de la sala/partida " + sala);

        }
    }

    // intenta unir al cliente a la sala indicada
    private boolean unirseASala(String nombreSala, UnCliente cliente, DataOutputStream salida) throws IOException {
        String resultado = grupoDB.unirseGrupo(nombreSala, cliente.getNombreUsuario());
        
        if (resultado.contains("Error") || resultado.contains("no existe")) {
            salida.writeUTF(resultado + " Intenta de nuevo.");
            return false;
        }
        
        cliente.establecerSalaActual(nombreSala);
        salida.writeUTF(resultado + " ¡Has entrado a la sala!");
        return true;
    }

    // crea una sala nueva
    private boolean crearSala(String nombreSala, UnCliente cliente, DataOutputStream salida) throws IOException {
        if (!nombreSala.matches("[a-zA-Z0-9]+")) {
            salida.writeUTF("Error: El nombre de la sala solo puede contener letras y números.");
            return false;
        }

        String resultadoCreacion = grupoDB.crearGrupo(nombreSala);

        if (resultadoCreacion.contains("Error") || resultadoCreacion.contains("ya existe")) {
            salida.writeUTF(resultadoCreacion + " Intenta de nuevo, hubo un error o el nombre ya es usado.");
            return false;
        }

        if (unirseASala(nombreSala, cliente, salida)) {
            salida.writeUTF(resultadoCreacion + " Te has unido automáticamente.");
            return true;
        } else {
            salida.writeUTF("Error: Sala creada, pero no se pudo unir automáticamente.");
            return false;
        }
    }

    // muestra el menú principal de salas
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

    // muestra las salas disponibles para unirse
    public void mostrarSalasDisponibles(UnCliente cliente, DataOutputStream salida) throws IOException {

        Map<String, Integer> salas = grupoDB.obtenerSalasDisponibles();
        
        StringBuilder lista = new StringBuilder("\n--- SALAS DISPONIBLES (Máx 6) ---\n");
        
        if (salas.isEmpty()) {
            lista.append("No hay salas disponibles. ¡Crea una con la opción 2!\n");
        } else {
            int contador = 1;
            // Iteramos sobre el mapa
            for (Map.Entry<String, Integer> entry : salas.entrySet()) {
                String nombreSala = entry.getKey();
                int cantidadJugadores = entry.getValue();
                
                lista.append(contador)
                     .append(". ")
                     .append(nombreSala)
                     .append(" (")
                     .append(cantidadJugadores)
                     .append("/6)\n"); // Formato (x/6)
                contador++;
            }
        }
        lista.append("------------------------------------------\n");
        lista.append("Escribe el NÚMERO de la sala para unirte (o /salir para volver):");
        
        salida.writeUTF(lista.toString());
        cliente.establecerEstadoActual(EstadoMenu.MENU_UNIRSE_SALA);
    }

    public void mostrarInterfazSalaActiva(UnCliente cliente, DataOutputStream salida) throws IOException {
        String limite = "Sin límite de mensajes."; 
        
        String menu = "\n" +
                      "--- SALA ACTIVA: #" + cliente.obtenerSalaActual() + " ---\n" +
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
}