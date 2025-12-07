package com.servidormulti.Flip7;

import com.servidormulti.UnCliente;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

public class SesionJuego {

    private final List<UnCliente> clientesEnSala;
    private final Map<String, Jugador> jugadores; // Mapa ID_Cliente -> JugadorLogico
    private final Baraja baraja;
    private final CalculadorPuntuacion calculadora;
    private final ManejadorAcciones manejadorAcciones;
    private boolean juegoIniciado = false;
    private int indiceTurnoActual = 0;
    private boolean esperandoObjetivo = false;
    private Carta accionPendiente = null;

    public SesionJuego(List<UnCliente> clientes) {
        this.clientesEnSala = clientes;
        this.jugadores = new HashMap<>();
        this.baraja = new Baraja();
        this.calculadora = new CalculadorPuntuacion();

        // Inicializamos el manejador de acciones
        this.manejadorAcciones = new ManejadorAcciones();
    }

    public void iniciarPartida() {
        if (juegoIniciado)
            return;

        jugadores.clear();
        for (UnCliente c : clientesEnSala) {
            jugadores.put(c.getClienteID(), new Jugador(c.getNombreUsuario()));
        }
        baraja.reiniciarBaraja();
        indiceTurnoActual = new Random().nextInt(clientesEnSala.size());

        juegoIniciado = true;
        esperandoObjetivo = false; // Resetear estado de acciones

        broadcastMensaje("--- ¡LA PARTIDA DE FLIP7 HA COMENZADO! ---");
        anunciarTurno();
    }

    public void procesarMensajeJuego(UnCliente remitente, String mensaje) {
        if (!juegoIniciado)
            return;

        UnCliente clienteActual = clientesEnSala.get(indiceTurnoActual);
        if (!remitente.getClienteID().equals(clienteActual.getClienteID())) {
            enviarMensajePrivado(remitente, "No es tu turno. Espera a " + clienteActual.getNombreUsuario());
            return;
        }

        Jugador jugadorActual = jugadores.get(remitente.getClienteID());

        // Separamos el comando de los argumentos (para /usar nombre)
        String[] partes = mensaje.trim().split("\\s+");
        String comando = partes[0].toLowerCase();
        if (esperandoObjetivo) {
            if (comando.equals("/usar")) {
                if (partes.length < 2) {
                    enviarMensajePrivado(remitente, "Debes especificar un nombre. Ej: /usar Juan");
                    return;
                }
                String nombreObjetivo = partes[1];
                ejecutarAccionPendiente(remitente, nombreObjetivo);
            } else {
                enviarMensajePrivado(remitente,
                        "¡Tienes una carta de ACCIÓN pendiente! Debes usarla con: /usar [NombreJugador]");
            }
            return; // Importante: No dejar pasar al switch normal
        }

        // FLUJO NORMAL DE JUEGO
        switch (comando) {
            case "/jalar":
                accionJalar(remitente, jugadorActual);
                break;
            case "/parar":
                accionParar(remitente, jugadorActual);
                break;
            default:
                enviarMensajePrivado(remitente, "Comando no válido en tu turno. Usa /jalar o /parar.");
        }
    }

    private void accionJalar(UnCliente cliente, Jugador jugador) {
        Carta carta = baraja.jalarCarta();

        if (carta == null) {
            broadcastMensaje("¡Se acabó la baraja! Barajeando descarte...");
            baraja.reiniciarBaraja();
            carta = baraja.jalarCarta();
        }

        // Doble verificación si el mazo sigue vacío después de reiniciar
        if (carta == null) {
            broadcastMensaje("Error: El mazo sigue vacío después de reiniciar. Finalizando ronda...");
            finalizarRonda();
            return;
        }

        broadcastMensaje(cliente.getNombreUsuario() + " jaló: " + carta);
        if (carta.obtenerTipo() == TipoCarta.ACCION) {
            procesarCartaAccion(cliente, jugador, carta);
            return; 
        }

        // Intentamos añadir la carta; sobrevivio es FALSE solo si hay BUST definitivo.
        boolean sobrevivio = jugador.intentarJalarCarta(carta);

        if (!sobrevivio) {
            broadcastMensaje("¡BUST! " + cliente.getNombreUsuario() + " ha perdido la ronda.");
            siguienteTurno();
        } else {
            if (calculadora.verificarFlip7(jugador.obtenerCartasEnMano())) {
                broadcastMensaje("\n FLIP 7 " + cliente.getNombreUsuario()
                        + " ha conseguido 7 cartas únicas. La ronda termina inmediatamente.");
                finalizarRonda(); // Llama al método que calcula puntos y avanza
                return;
            }

            // Si no hubo Flip 7 ni BUST, se ofrece seguir jalando o parar
            enviarMensajePrivado(cliente, "Tu mano actual: " + jugador.obtenerCartasEnMano());
            enviarMensajePrivado(cliente, "¿Quieres /jalar otra o /parar?");
        }
    }

    private void procesarCartaAccion(UnCliente cliente, Jugador jugador, Carta carta) {
        String nombreCarta = carta.toString();

        if (nombreCarta.equals("Second Chance")) {
            // Si ya tiene, debe darla a otro. Si no, se la queda.
            if (jugador.tieneSecondChance()) {
                this.accionPendiente = carta;
                this.esperandoObjetivo = true;
                enviarMensajePrivado(cliente,
                        "¡Ya tienes una Second Chance! Debes regalar esta a otro jugador. Escribe: /usar [NombreJugador]");
            } else {
                // Se la queda automáticamente
                jugador.setTieneSecondChance(true);
                broadcastMensaje(cliente.getNombreUsuario() + " ha obtenido una Second Chance (Vida Extra).");
                // El turno continúa
                enviarMensajePrivado(cliente, "¿Quieres /jalar otra o /parar?");
            }

        } else if (nombreCarta.equals("Freeze") || nombreCarta.equals("Flip Three")) {
            // Estas siempre requieren un objetivo
            this.accionPendiente = carta;
            this.esperandoObjetivo = true;
            enviarMensajePrivado(cliente,
                    "¡Has sacado " + nombreCarta + "! Debes aplicarla a alguien. Escribe: /usar [NombreJugador]");
        }
    }

    private void ejecutarAccionPendiente(UnCliente atacante, String nombreObjetivo) {
        // Buscar al cliente objetivo por nombre
        UnCliente clienteObj = null;
        for (UnCliente c : clientesEnSala) {
            if (c.getNombreUsuario().equalsIgnoreCase(nombreObjetivo)) {
                clienteObj = c;
                break;
            }
        }

        if (clienteObj == null) {
            enviarMensajePrivado(atacante,
                    "Jugador '" + nombreObjetivo + "' no encontrado en la sala. Intenta de nuevo.");
            return;
        }

        // Obtener el Jugador lógico
        Jugador objetivo = jugadores.get(clienteObj.getClienteID());
        String resultado = "";

        // Ejecutar la acción correspondiente
        String nombreAccion = accionPendiente.toString();

        if (nombreAccion.equals("Second Chance")) {
            resultado = manejadorAcciones.transferirSecondChance(objetivo);

        } else if (nombreAccion.equals("Freeze")) {
            resultado = manejadorAcciones.aplicarFreeze(objetivo);

        } else if (nombreAccion.equals("Flip Three")) {
            resultado = manejadorAcciones.aplicarFlipThree(objetivo, baraja);
        }

        // Anunciar resultado
        broadcastMensaje("ACCIÓN " + nombreAccion + ": " + resultado);

        // Limpiar estado de acción
        this.esperandoObjetivo = false;
        this.accionPendiente = null;

        // El turno continúa para el jugador actual después de usar la acción
        enviarMensajePrivado(atacante, "¿Quieres /jalar otra o /parar?");
    }

    private void accionParar(UnCliente cliente, Jugador jugador) {
        jugador.plantarse();
        int puntos = calculadora.calcularPuntuacion(jugador.obtenerCartasEnMano());
        broadcastMensaje(cliente.getNombreUsuario() + " se ha PLANTADO con " + puntos + " puntos provisionales.");
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
            UnCliente proximoCliente = clientesEnSala.get(indiceTurnoActual);
            Jugador proximoJugador = jugadores.get(proximoCliente.getClienteID());

            // Solo pasa el turno si NO tiene BUST y NO se plantó
            if (!proximoJugador.tieneBUST() && !proximoJugador.sePlanto()) {
                anunciarTurno();
                return;
            }
            intentos++;
        } while (intentos < clientesEnSala.size());

        finalizarRonda();
    }

    private boolean verificarFinDeRonda() {
        for (Jugador j : jugadores.values()) {
            if (!j.tieneBUST() && !j.sePlanto()) {
                return false;
            }
        }
        return true;
    }

    private void finalizarRonda() {
        broadcastMensaje("\n--- FIN DE LA RONDA ---");
        broadcastMensaje("Resultados:");

        for (UnCliente c : clientesEnSala) {
            Jugador j = jugadores.get(c.getClienteID());
            int puntos = j.tieneBUST() ? 0 : calculadora.calcularPuntuacion(j.obtenerCartasEnMano());
            broadcastMensaje(
                    " -> " + c.getNombreUsuario() + ": " + puntos + " puntos. (" + j.obtenerCartasEnMano() + ")");
        }
        juegoIniciado = false;
        broadcastMensaje("Escriban /listo para iniciar otra vez.");
    }

    private void anunciarTurno() {
        UnCliente actual = clientesEnSala.get(indiceTurnoActual);
        broadcastMensaje("\n>>> Turno de: " + actual.getNombreUsuario() + " <<<");
        enviarMensajePrivado(actual, "Es tu turno. Escribe /jalar o /parar");
    }

    private void broadcastMensaje(String msg) {
        for (UnCliente c : clientesEnSala) {
            try {
                c.getSalida().writeUTF(msg);
            } catch (IOException e) {
                /* Ignorar desconexión */ }
        }
    }

    private void enviarMensajePrivado(UnCliente c, String msg) {
        try {
            c.getSalida().writeUTF(msg);
        } catch (IOException e) {
            /* Ignorar */ }
    }

    public boolean estaJuegoIniciado() {
        return juegoIniciado;
    }
}