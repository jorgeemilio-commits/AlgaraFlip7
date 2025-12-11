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
    
    private final Map<String, SesionJuego> partidasActivas;
    private final Map<String, Set<String>> votosListo;

    public ManejadorSalas(GrupoDB grupoDB, ManejadorMensajes manejadorMensajes) {
        this.grupoDB = grupoDB;
        this.manejadorMensajes = manejadorMensajes;
        this.partidasActivas = new HashMap<>();
        this.votosListo = new HashMap<>();
    }

    public void procesar(String mensaje, UnCliente cliente, DataOutputStream salida) throws IOException {
        ManejadorMenu menu = cliente.getManejadorMenu(); // Referencia corta para usar abajo

        switch (cliente.obtenerEstadoActual()) {
            
            case MENU_SALA_PRINCIPAL:
                if (mensaje.equals("1")) { // Unirse a una sala
                    // Lógica: Obtener datos -> Pasarlos a la vista
                    Map<String, Integer> salas = grupoDB.obtenerSalasDisponibles();
                    menu.mostrarSalasDisponibles(cliente, salida, salas);
                    
                } else if (mensaje.equals("2")) { // Crear una sala
                    if (!cliente.estaLogueado()) { 
                        salida.writeUTF("Error: Solo los usuarios registrados pueden crear salas.");
                        menu.mostrarMenuSalaPrincipal(cliente, salida);
                    } else {
                        cliente.establecerEstadoActual(EstadoMenu.MENU_CREAR_SALA_NOMBRE); 
                        salida.writeUTF("Introduce el nombre de la nueva sala (solo letras/números):");
                    }
                    
                } else if (mensaje.equals("3")) { // Cerrar sesión
                    cliente.manejarLogout(); 
                    menu.mostrarMenuPrincipal(cliente, salida);
                    
                } else {
                    salida.writeUTF("Opción no válida. Por favor, ingresa 1, 2, o 3.");
                    menu.mostrarMenuSalaPrincipal(cliente, salida);
                }
                break;

            case MENU_UNIRSE_SALA:
                if (mensaje.trim().equalsIgnoreCase("/salir")) { 
                    menu.mostrarMenuSalaPrincipal(cliente, salida);
                    return;
                }
                
                try { 
                    Map<String, Integer> salasMap = grupoDB.obtenerSalasDisponibles();
                    List<String> salasLista = new ArrayList<>(salasMap.keySet());
                    
                    int indiceSeleccionado = Integer.parseInt(mensaje.trim());
                    
                    if (indiceSeleccionado >= 1 && indiceSeleccionado <= salasLista.size()) {
                        String nombreSala = salasLista.get(indiceSeleccionado - 1); 
                        
                        if (unirseASala(nombreSala, cliente, salida)) {
                            menu.mostrarInterfazSalaActiva(cliente, salida, nombreSala);
                        } else {
                            menu.mostrarMenuSalaPrincipal(cliente, salida);
                        }
                    } else { 
                        salida.writeUTF("Número de sala no válido. Elige un número de la lista o escribe /salir.");
                        // Reutilizamos la lógica de mostrar disponibles
                        menu.mostrarSalasDisponibles(cliente, salida, salasMap);
                    }
                    
                } catch (NumberFormatException e) { 
                    salida.writeUTF("Entrada no válida. Debes escribir el NÚMERO de la sala.");
                    // Re-fetch para mostrar
                    menu.mostrarSalasDisponibles(cliente, salida, grupoDB.obtenerSalasDisponibles());
                }
                break;

            case MENU_CREAR_SALA_NOMBRE:
                if (crearSala(mensaje, cliente, salida)) { 
                    menu.mostrarInterfazSalaActiva(cliente, salida, mensaje); // mensaje es el nombreSala
                } else {
                    menu.mostrarMenuSalaPrincipal(cliente, salida);
                }
                break;

            case SALA_ACTIVA:
                if (mensaje.trim().equalsIgnoreCase("/salir")) { 
                    salirDelGrupoActual(cliente);
                    menu.mostrarMenuSalaPrincipal(cliente, salida);

                } else if (mensaje.trim().equalsIgnoreCase("/jugadores") || mensaje.trim().equalsIgnoreCase("/miembros")) {
                    // Lógica para preparar datos de jugadores
                    String nombreSala = cliente.obtenerSalaActual();
                    if (nombreSala != null) {
                        Integer grupoId = grupoDB.getGrupoId(nombreSala);
                        if (grupoId != null) {
                            List<String> miembros = grupoDB.getMiembrosGrupo(grupoId);
                            menu.mostrarJugadoresEnSala(cliente, salida, miembros, nombreSala);
                        } else {
                            salida.writeUTF("Error: La sala no existe.");
                        }
                    } else {
                        salida.writeUTF("Error: No estás en una sala.");
                    }

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
                        menu.mostrarMenuSalaPrincipal(cliente, salida);
                    }
                }
                break;

            default:
                salida.writeUTF("Error de estado de salas.");
                menu.mostrarMenuSalaPrincipal(cliente, salida);
                break;
        }
    }

    // Maneja el comando /listo
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

    // Maneja el comando /nolisto
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

    // Inicia la partida en la sala especificada
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

    // Lógica para salir del grupo/sala actual
    public void salirDelGrupoActual(UnCliente cliente) { 
        String sala = cliente.obtenerSalaActual();
        if (sala != null) {
            grupoDB.salirGrupo(sala, cliente.getNombreUsuario());
            
            if (votosListo.containsKey(sala)) {
                votosListo.get(sala).remove(cliente.getNombreUsuario());
            }

            SesionJuego juego = partidasActivas.get(sala);
            if (juego != null) {
               juego.removerJugador(cliente);

               if (grupoDB.obtenerSalasDisponibles().get(sala) == null) {
                   partidasActivas.remove(sala);
               }
            }

            cliente.establecerSalaActual(null);
            System.out.println("Jugador " + cliente.getNombreUsuario() + " removido de la sala/partida " + sala);
        }
    }

    // Lógica para unirse a una sala
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

    // Lógica para crear una sala
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

    public Map<Integer, String> obtenerPartidasGuardadas(String usuario) {
        return grupoDB.obtenerPartidasGuardadas(usuario);
    }
}