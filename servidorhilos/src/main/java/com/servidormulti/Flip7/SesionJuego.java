package com.servidormulti.Flip7;

import com.servidormulti.UnCliente;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

public class SesionJuego {

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

        jugadores.clear();
        for (UnCliente c : clientesEnSala) {
            jugadores.put(c.getClienteID(), new Jugador(c.getNombreUsuario()));
        }

        baraja.reiniciarBaraja();
        juegoIniciado = true;
        esperandoObjetivo = false;
        
        vista.mostrarInicioPartida(clientesEnSala);
        configurarYArrancarRonda();
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

        if (!mensaje.trim().startsWith("/")) {
            vista.mostrarMensajeChat(clientesEnSala, remitente.getNombreUsuario(), mensaje);
            return;
        }

        String[] partes = mensaje.trim().split("\\s+");
        String comando = partes[0].toLowerCase();

        if (comando.equals("/puntuacion")) {
            vista.mostrarReportePuntuacion(remitente, jugadores);
            return;
        }

        UnCliente clienteActual = clientesEnSala.get(indiceTurnoActual);
        boolean esTurno = remitente.getClienteID().equals(clienteActual.getClienteID());
        boolean esVictimaFlip3 = (this.flipThreeObjetivo != null &&
                remitente.getNombreUsuario().equalsIgnoreCase(this.flipThreeObjetivo.obtenerNombreUsuario()));

        if (!esTurno && !esVictimaFlip3) {
            vista.enviar(remitente, "No es tu turno. Espera a " + clienteActual.getNombreUsuario());
            return;
        }

        Jugador jugadorActual = jugadores.get(remitente.getClienteID());

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
                vista.enviar(remitente, "Comando no válido. Usa /jalar, /parar, /puntuacion.");
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