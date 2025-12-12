package com.servidormulti.Flip7;

import com.servidormulti.UnCliente;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import com.servidormulti.ManejadorSalas;

public class SesionJuego {

    private Set<String> votosGuardar = new HashSet<>();
    private final List<UnCliente> clientesEnSala;
    private final Map<String, Jugador> jugadores; 
    private final Baraja baraja;
    private final CalculadorPuntuacion calculadora;
    private final ManejadorAcciones manejadorAcciones;
    
    // Nueva Referencia a la Vista
    private final VistaJuego vista;

    private boolean juegoIniciado = false;
    private int indiceTurnoActual = 0;
    private boolean esperandoObjetivo = false;
    private Carta accionPendiente = null;
    
    private List<Carta> accionesAcumuladasFlipThree = new ArrayList<>();
    private int flipThreeCartasRestantes = 0;
    private Jugador flipThreeObjetivo = null;
    private UnCliente flipThreeAtacante = null; // Mantenemos referencia aunque no se use mucho

    private static final long TIEMPO_ENTRE_CARTAS_FLIP3 = 1000; // 1 segundo entre cartas
    private static final long TIEMPO_ESPERA_NUEVA_RONDA = 15000; // 15 segundos antes de nueva ronda

    public SesionJuego(List<UnCliente> clientes) {
        this.clientesEnSala = clientes;
        this.jugadores = new HashMap<>();
        this.baraja = new Baraja();
        this.calculadora = new CalculadorPuntuacion();
        this.manejadorAcciones = new ManejadorAcciones();
        
        // Inicializamos la vista
        this.vista = new VistaJuego();
    }

    public void iniciarPartida() {
        if (juegoIniciado) return;

        // Limpieza inicial
        jugadores.clear();
        for (UnCliente c : clientesEnSala) {
            jugadores.put(c.getClienteID(), new Jugador(c.getNombreUsuario()));
        }

        // --- LÓGICA DE CARGA O REINICIO ---
        String nombreSala = clientesEnSala.get(0).obtenerSalaActual();
        com.servidormulti.GrupoDB db = new com.servidormulti.GrupoDB(); // Instancia temporal
        Integer idGuardado = db.obtenerIdPartidaPorSala(nombreSala);

        if (idGuardado != null) {
            vista.mostrarMensajeGenerico(clientesEnSala, "¡PARTIDA GUARDADA ENCONTRADA! Cargando estado...");
            boolean cargaExitosa = cargarEstadoDeBaseDeDatos(idGuardado, db);
            if (cargaExitosa) {
                // Borramos el guardado para que no se cargue infinitamente si reinician de nuevo
                db.eliminarPartidaGuardada(idGuardado);
            } else {
                vista.mostrarMensajeGenerico(clientesEnSala, "Error cargando. Iniciando partida nueva.");
                baraja.reiniciarBaraja();
            }
        } else {
            // Partida Normal
            baraja.reiniciarBaraja();
        }
        // ----------------------------------

        juegoIniciado = true;
        esperandoObjetivo = false;
        
        vista.mostrarInicioPartida(clientesEnSala);
        
        // Si cargamos partida, anunciamos el turno guardado, si no, random
        if (idGuardado == null) {
            if (!clientesEnSala.isEmpty()) {
                indiceTurnoActual = new java.util.Random().nextInt(clientesEnSala.size());
            }
        }
        anunciarTurno();
    }

    // Carga el estado de la partida desde la base de datos
    private boolean cargarEstadoDeBaseDeDatos(int id, com.servidormulti.GrupoDB db) {
        try {
            this.indiceTurnoActual = db.obtenerTurnoGuardado(id);
            List<com.servidormulti.GrupoDB.DatosJugadorGuardado> datos = db.cargarJugadoresDePartida(id);

            baraja.reiniciarBaraja(); 

            for (com.servidormulti.GrupoDB.DatosJugadorGuardado d : datos) {
                // Buscar al jugador conectado que coincida con el nombre
                UnCliente clienteDueño = null;
                for(UnCliente c : clientesEnSala) {
                    if(c.getNombreUsuario().equals(d.nombre)) {
                        clienteDueño = c;
                        break;
                    }
                }
                
                if (clienteDueño != null) {
                    Jugador j = jugadores.get(clienteDueño.getClienteID());
                    j.sumarPuntos(d.puntuacion); // Restaurar puntos base
                    j.setTieneSecondChance(d.secondChance);
                    j.setTieneBUST(d.esBust);// Restaura si perdió
                    j.setSePlanto(d.sePlanto);// Restaura si se plantó (/parar)
                    j.setEstaCongelado(d.estaCongelado);
                    
                    // Restaurar cartas en mano
                    if (d.cartas != null && !d.cartas.isEmpty()) {
                        String[] cartasArr = d.cartas.split(",");
                        for (String nombreCarta : cartasArr) {
                            Carta c = reconstruirCarta(nombreCarta);
                            if (c != null) j.obtenerCartasEnMano().add(c);
                        }
                    }
                }
            }
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    // Construye una carta a partir de su nombre 
    private Carta reconstruirCarta(String nombre) {
        try {
            if (nombre.equals("Second Chance")) return new Carta(0, "Second Chance", TipoCarta.ACCION);
            if (nombre.equals("Freeze")) return new Carta(0, "Freeze", TipoCarta.ACCION);
            if (nombre.equals("Flip Three")) return new Carta(0, "Flip Three", TipoCarta.ACCION);
            if (nombre.equals("x2")) return new Carta(0, "x2", TipoCarta.BONUS);
            if (nombre.equals("+10")) return new Carta(0, "+10", TipoCarta.BONUS);
            
            int valor = Integer.parseInt(nombre);
            return new Carta(valor, String.valueOf(valor), TipoCarta.NUMERICA);
        } catch (Exception e) {
            return null;
        }
    }

    private void iniciarSiguienteRonda() {
        if (clientesEnSala.isEmpty()) {
            juegoIniciado = false;
            return;
        }

        for (Jugador j : jugadores.values()) {
            j.reiniciarParaRondaNueva();
        }
        
        juegoIniciado = true;
        esperandoObjetivo = false;
        vista.mostrarInicioRonda(clientesEnSala, 0); // Podríamos llevar cuenta de rondas
        configurarYArrancarRonda();
    }

    private void configurarYArrancarRonda() {
        if (!clientesEnSala.isEmpty()) {
            indiceTurnoActual = new Random().nextInt(clientesEnSala.size());
        }
        anunciarTurno();
    }

    public void procesarMensajeJuego(UnCliente remitente, String mensaje) {
        if (!juegoIniciado) return;

        // 1. Si no empieza con '/', es un mensaje de chat normal
        if (!mensaje.trim().startsWith("/")) {
            vista.mostrarMensajeChat(clientesEnSala, remitente.getNombreUsuario(), mensaje);
            return;
        }

        String[] partes = mensaje.trim().split("\\s+");
        String comando = partes[0].toLowerCase();

        // --- COMANDO DE GUARDADO  ---
        if (comando.equals("/guardar")) {
            votosGuardar.add(remitente.getNombreUsuario());
            int total = clientesEnSala.size();
            int actuales = votosGuardar.size();
            
            vista.mostrarMensajeGenerico(clientesEnSala, 
                remitente.getNombreUsuario() + " quiere GUARDAR la partida (" + actuales + "/" + total + ").");

            if (actuales == total) {
                // Instanciamos la nueva clase y llamamos al método
                GuardadoPartida guardador = new GuardadoPartida();
                
                boolean guardadoExitoso = guardador.guardarYTerminar(
                    clientesEnSala, 
                    jugadores, 
                    indiceTurnoActual, 
                    vista
                );

                // Si se guardó bien, limpiamos la sesión en memoria
                if (guardadoExitoso) {
                    clientesEnSala.clear();
                    juegoIniciado = false;
                }
            }
            return;
        }
        // -----------------------------------

        if (comando.equals("/puntuacion")) {
            vista.mostrarReportePuntuacion(remitente, jugadores);
            return;
        }

        // 2. Validación de Turno
        UnCliente clienteActual = clientesEnSala.get(indiceTurnoActual);
        boolean esTurno = remitente.getClienteID().equals(clienteActual.getClienteID());
        // Validación especial para quien está sufriendo un "Flip Three" (puede actuar fuera de turno normal)
        boolean esVictimaFlip3 = (this.flipThreeObjetivo != null &&
                remitente.getNombreUsuario().equalsIgnoreCase(this.flipThreeObjetivo.obtenerNombreUsuario()));

        if (!esTurno && !esVictimaFlip3) {
            vista.enviar(remitente, "No es tu turno. Espera a " + clienteActual.getNombreUsuario());
            return;
        }

        Jugador jugadorActual = jugadores.get(remitente.getClienteID());

        // 3. Manejo de cartas de ACCIÓN pendientes (Freeze, Flip Three, etc.)
        if (esperandoObjetivo) {
            if (comando.equals("/usar")) {
                if (partes.length < 2) {
                    vista.enviar(remitente, "Debes especificar un nombre. Ej: /usar Juan");
                    return;
                }
                ejecutarAccionPendiente(remitente, partes[1]);
            } else {
                vista.enviar(remitente, "¡Tienes una carta de ACCION pendiente! Usa: /usar [Nombre]");
            }
            return;
        }

        // 4. Comandos de Juego normales
        switch (comando) {
            case "/jalar":
                if (esVictimaFlip3 && !esTurno) {
                    vista.enviar(remitente, "Estás en un Flip Three. Solo puedes usar /usar cuando se te pida.");
                    return;
                }
                accionJalar(remitente, jugadorActual);
                break;
            case "/parar":
                if (esVictimaFlip3 && !esTurno) return;
                accionParar(remitente, jugadorActual);
                break;
            default:
                vista.enviar(remitente, "Comando no válido. Usa /jalar, /parar, /puntuacion o /guardar.");
        }
    }

    private void accionJalar(UnCliente cliente, Jugador jugador) {
        Carta carta = baraja.jalarCarta();

        if (carta == null) {
            vista.mostrarMensajeGenerico(clientesEnSala, "¡Se acabó la baraja! Barajeando descarte...");
            baraja.reiniciarBaraja();
            carta = baraja.jalarCarta();
        }

        if (carta == null) {
            vista.mostrarMensajeGenerico(clientesEnSala, "Error: El mazo sigue vacío. Finalizando ronda...");
            finalizarRonda();
            return;
        }

        vista.mostrarCartaJalada(clientesEnSala, cliente, carta);

        if (carta.obtenerTipo() == TipoCarta.ACCION) {
            procesarCartaAccion(cliente, jugador, carta);
            return;
        }

        boolean teniaVida = jugador.tieneSecondChance();
        boolean sobrevivio = jugador.intentarJalarCarta(carta);

        if (sobrevivio && teniaVida && !jugador.tieneSecondChance()) {
            vista.mostrarSalvacionSecondChance(clientesEnSala, cliente.getNombreUsuario(), carta);
        }

        if (!sobrevivio) {
            vista.mostrarBust(clientesEnSala, cliente.getNombreUsuario());
            siguienteTurno();
        } else {
            if (calculadora.verificarFlip7(jugador.obtenerCartasEnMano())) {
                vista.mostrarFlip7(clientesEnSala, cliente.getNombreUsuario());
                finalizarRonda();
                return;
            }
            vista.enviar(cliente, "Tu mano actual: " + jugador.obtenerCartasEnMano());
            siguienteTurno();
        }
    }

    private void procesarCartaAccion(UnCliente cliente, Jugador jugador, Carta carta) {
        String nombreCarta = carta.toString();
        List<String> objetivosValidos = new ArrayList<>();

        // Lógica de filtrado de objetivos (sin strings de UI)
        if (nombreCarta.equals("Second Chance") && jugador.tieneSecondChance()) {
            for (UnCliente c : clientesEnSala) {
                if (c.getClienteID().equals(cliente.getClienteID())) continue;
                Jugador j = jugadores.get(c.getClienteID());
                if (!j.tieneBUST() && !j.sePlanto() && !j.tieneSecondChance()) {
                    objetivosValidos.add(c.getNombreUsuario());
                }
            }
        } 
        else if (nombreCarta.equals("Freeze") || nombreCarta.equals("Flip Three")) {
            for (UnCliente c : clientesEnSala) {
                Jugador j = jugadores.get(c.getClienteID());
                if (!j.sePlanto() && !j.tieneBUST()) {
                    String nombre = c.getNombreUsuario();
                    if (c.getClienteID().equals(cliente.getClienteID())) nombre += " [TU]";
                    objetivosValidos.add(nombre);
                }
            }
        }
        else if (nombreCarta.equals("Second Chance") && !jugador.tieneSecondChance()) {
            jugador.setTieneSecondChance(true);
            vista.mostrarObtencionSecondChance(clientesEnSala, cliente.getNombreUsuario());
            siguienteTurno();
            return;
        }

        // Si hay objetivos, mostramos el menú usando la Vista
        if (!objetivosValidos.isEmpty()) {
            this.accionPendiente = carta;
            this.esperandoObjetivo = true;
            vista.mostrarMenuSeleccionObjetivo(cliente, nombreCarta, objetivosValidos);
        } else {
            // Si no hay objetivos, se descarta y pasa turno
            vista.mostrarMensajeGenerico(clientesEnSala, cliente.getNombreUsuario() + " sacó " + nombreCarta + " pero no hay objetivos. Se DESCARTA.");
            siguienteTurno();
        }
    }

    private void ejecutarAccionPendiente(UnCliente atacante, String nombreObjetivo) {
        // Normalizamos quitando el [TÚ] si el usuario lo escribió
        String nombreLimpio = nombreObjetivo.replace(" [TU]", "").trim();
        
        UnCliente clienteObj = buscarClientePorNombre(nombreLimpio);

        if (clienteObj == null) {
            vista.mostrarAccionFallida(atacante, "Jugador no encontrado.");
            return;
        }

        Jugador objetivo = jugadores.get(clienteObj.getClienteID());
        String nombreAccion = accionPendiente.toString();

        // Validaciones lógicas
        if ((objetivo.sePlanto() || objetivo.tieneBUST()) && !nombreAccion.equals("Second Chance")) { // Second Chance lógica aparte
             vista.mostrarAccionFallida(atacante, "El jugador no está activo.");
             return;
        }
        if (nombreAccion.equals("Second Chance") && objetivo.tieneSecondChance()) {
             vista.mostrarAccionFallida(atacante, "Ya tiene Second Chance.");
             return;
        }

        if (nombreAccion.equals("Flip Three")) {
            this.flipThreeCartasRestantes = 3;
            this.flipThreeObjetivo = objetivo;
            this.flipThreeAtacante = atacante;
            this.accionesAcumuladasFlipThree.clear();
            this.esperandoObjetivo = false;
            this.accionPendiente = null;
            iniciarOReanudarFlipThree(atacante, objetivo);
            return;
        } 
        
        // Ejecución de Freeze o Second Chance
        if (nombreAccion.equals("Second Chance")) manejadorAcciones.transferirSecondChance(objetivo);
        else if (nombreAccion.equals("Freeze")) manejadorAcciones.aplicarFreeze(objetivo);

        vista.mostrarAccionEjecutada(clientesEnSala, nombreAccion, objetivo.obtenerNombreUsuario());

        this.esperandoObjetivo = false;
        this.accionPendiente = null;

        if (this.flipThreeObjetivo != null) {
            procesarSiguienteAccionAcumulada();
        } else {
            siguienteTurno();
        }
    }

    private void iniciarOReanudarFlipThree(UnCliente atacante, Jugador objetivo) {
        this.esperandoObjetivo = false;
        this.accionPendiente = null;
        if (this.flipThreeCartasRestantes == 3) this.accionesAcumuladasFlipThree.clear();

        while (this.flipThreeCartasRestantes > 0 && !objetivo.tieneBUST()) {
            
            if (calculadora.verificarFlip7(objetivo.obtenerCartasEnMano())) {
                vista.mostrarMensajeGenerico(clientesEnSala, "¡FLIP 7 conseguido durante Flip Three! Se detiene la secuencia.");
                finalizarRonda();
                return;
            }

            vista.mostrarEstadoFlipThree(clientesEnSala, objetivo.obtenerNombreUsuario(), this.flipThreeCartasRestantes);

            Carta carta = baraja.jalarCarta();
            // ... lógica de baraja vacía omitida por brevedad, igual que antes ...
            if (carta == null) break;

            this.flipThreeCartasRestantes--;

            if (carta.obtenerTipo() == TipoCarta.ACCION) {
                if (carta.toString().equals("Second Chance") && !objetivo.tieneSecondChance()) {
                    objetivo.setTieneSecondChance(true);
                    vista.mostrarObtencionSecondChance(clientesEnSala, objetivo.obtenerNombreUsuario());
                } else {
                    vista.mostrarMensajeGenerico(clientesEnSala, "Salió " + carta + ". Se guarda para después.");
                    accionesAcumuladasFlipThree.add(carta);
                }
            } else {
                vista.mostrarCartaFlipThree(clientesEnSala, carta);
                boolean sobrevivio = objetivo.intentarJalarCarta(carta);
                if (!sobrevivio) {
                    vista.mostrarBust(clientesEnSala, objetivo.obtenerNombreUsuario());
                    accionesAcumuladasFlipThree.clear();
                    break;
                }
            }

            try { Thread.sleep(TIEMPO_ENTRE_CARTAS_FLIP3); } catch (InterruptedException e) {}
        }

        if (objetivo.tieneBUST()) {
            this.flipThreeCartasRestantes = 0;
            this.flipThreeObjetivo = null;
            siguienteTurno();
        } else {
            if (!accionesAcumuladasFlipThree.isEmpty()) {
                vista.mostrarMensajeGenerico(clientesEnSala, "Flip Three finalizado. Aplicando acciones acumuladas...");
                procesarSiguienteAccionAcumulada();
            } else {
                vista.mostrarMensajeGenerico(clientesEnSala, "Flip Three finalizado limpio.");
                this.flipThreeObjetivo = null;
                siguienteTurno();
            }
        }
    }

    private void procesarSiguienteAccionAcumulada() {
        if (accionesAcumuladasFlipThree.isEmpty()) {
            this.flipThreeObjetivo = null;
            this.flipThreeAtacante = null;
            siguienteTurno();
            return;
        }

        Carta cartaAccion = accionesAcumuladasFlipThree.remove(0);
        
        // El dueño de las acciones acumuladas es quien recibió el Flip Three (el objetivo original)
        UnCliente dueno = buscarClientePorNombre(flipThreeObjetivo.obtenerNombreUsuario());
        
        if (dueno != null) {
            // Tratamos la carta como si el jugador la acabara de sacar
            procesarCartaAccion(dueno, flipThreeObjetivo, cartaAccion);
        }
    }

    private void accionParar(UnCliente cliente, Jugador jugador) {
        jugador.plantarse();
        int puntos = calculadora.calcularPuntuacion(jugador.obtenerCartasEnMano());
        vista.mostrarPlantarse(clientesEnSala, cliente.getNombreUsuario(), puntos);
        siguienteTurno();
    }

    private void siguienteTurno() {
        if (verificarFinDeRonda()) {
            finalizarRonda();
            return;
        }
        int intentos = 0;
        do {
            indiceTurnoActual = (indiceTurnoActual + 1) % clientesEnSala.size();
            UnCliente proximo = clientesEnSala.get(indiceTurnoActual);
            Jugador jProximo = jugadores.get(proximo.getClienteID());

            if (!jProximo.tieneBUST() && !jProximo.sePlanto()) {
                anunciarTurno();
                return;
            }
            intentos++;
        } while (intentos < clientesEnSala.size());
        finalizarRonda();
    }

    private boolean verificarFinDeRonda() {
        for (Jugador j : jugadores.values()) {
            if (!j.tieneBUST() && !j.sePlanto()) return false;
        }
        return true;
    }

    private void finalizarRonda() {
        Jugador ganadorDelJuego = null;
        int maxPuntuacionGlobal = -1;
        StringBuilder resumen = new StringBuilder("Resultados:\n");

        for (UnCliente c : clientesEnSala) {
            Jugador j = jugadores.get(c.getClienteID());
            int puntosRonda = j.tieneBUST() ? 0 : calculadora.calcularPuntuacion(j.obtenerCartasEnMano());
            j.sumarPuntos(puntosRonda);
            
            resumen.append(" -> ").append(c.getNombreUsuario())
                   .append(": +").append(puntosRonda)
                   .append(" (Total: ").append(j.obtenerPuntuacionTotal()).append(")\n");

            if (j.obtenerPuntuacionTotal() >= 200 && j.obtenerPuntuacionTotal() > maxPuntuacionGlobal) {
                maxPuntuacionGlobal = j.obtenerPuntuacionTotal();
                ganadorDelJuego = j;
            }
        }

        vista.mostrarResultadosRonda(clientesEnSala, resumen.toString());
        juegoIniciado = false;

        if (ganadorDelJuego != null) {
            vista.mostrarFinJuego(clientesEnSala, ganadorDelJuego.obtenerNombreUsuario(), maxPuntuacionGlobal);
        } else {
            vista.mostrarEsperaNuevaRonda(clientesEnSala);
            new Thread(() -> {
                try {
                    Thread.sleep(TIEMPO_ESPERA_NUEVA_RONDA);
                    vista.mostrarMensajeGenerico(clientesEnSala, "¡Tiempo fuera! Preparando cartas...");
                    iniciarSiguienteRonda();
                } catch (InterruptedException e) { e.printStackTrace(); }
            }).start();
        }
    }

    private void anunciarTurno() {
        if (clientesEnSala.isEmpty()) return;
        if (indiceTurnoActual >= clientesEnSala.size()) indiceTurnoActual = 0;

        UnCliente actual = clientesEnSala.get(indiceTurnoActual);
        Jugador jActual = jugadores.get(actual.getClienteID());
        
        vista.anunciarTurno(clientesEnSala, actual, jActual);
    }

    public synchronized void removerJugador(UnCliente cliente) {
        if (!juegoIniciado) {
            clientesEnSala.remove(cliente);
            jugadores.remove(cliente.getClienteID());
            vista.mostrarMensajeGenerico(clientesEnSala, cliente.getNombreUsuario() + " ha abandonado la sala.");
            return;
        }

        vista.mostrarMensajeGenerico(clientesEnSala, "¡" + cliente.getNombreUsuario() + " abandonó la partida!");
        int indiceSeVa = clientesEnSala.indexOf(cliente);
        if (indiceSeVa == -1) return;

        boolean eraSuTurno = (indiceSeVa == indiceTurnoActual);
        clientesEnSala.remove(indiceSeVa);
        jugadores.remove(cliente.getClienteID());

        if (clientesEnSala.size() == 1) {
            UnCliente ganador = clientesEnSala.get(0);
            vista.mostrarMensajeGenerico(clientesEnSala, "\n¡VICTORIA POR ABANDONO! EL GANADOR ES: " + ganador.getNombreUsuario());
            juegoIniciado = false;
            return;
        }

        if (indiceSeVa < indiceTurnoActual) indiceTurnoActual--;
        if (indiceTurnoActual >= clientesEnSala.size()) indiceTurnoActual = 0;

        if (eraSuTurno) {
            vista.mostrarMensajeGenerico(clientesEnSala, "El jugador actual se fue. Pasando turno...");
            anunciarTurno();
        }
    }
    
    private UnCliente buscarClientePorNombre(String nombre) {
        for (UnCliente c : clientesEnSala) {
            if (c.getNombreUsuario().equalsIgnoreCase(nombre)) return c;
        }
        return null;
    }

    public boolean estaJuegoIniciado() { return juegoIniciado; }
}