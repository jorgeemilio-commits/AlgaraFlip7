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
    
    private boolean juegoIniciado = false;
    private int indiceTurnoActual = 0;

    public SesionJuego(List<UnCliente> clientes) {
        this.clientesEnSala = clientes;
        this.jugadores = new HashMap<>();
        this.baraja = new Baraja();
        this.calculadora = new CalculadorPuntuacion();
    }

    public void iniciarPartida() {
        if (juegoIniciado) return;

        jugadores.clear();
        for (UnCliente c : clientesEnSala) {
            jugadores.put(c.getClienteID(), new Jugador(c.getNombreUsuario()));
        }
        baraja.reiniciarBaraja();
        indiceTurnoActual = new Random().nextInt(clientesEnSala.size());
        juegoIniciado = true;

        broadcastMensaje("--- ¡LA PARTIDA DE FLIP7 HA COMENZADO! ---");
        anunciarTurno();
    }

    public void procesarMensajeJuego(UnCliente remitente, String mensaje) {
        if (!juegoIniciado) return;

        UnCliente clienteActual = clientesEnSala.get(indiceTurnoActual);
        if (!remitente.getClienteID().equals(clienteActual.getClienteID())) {
            enviarMensajePrivado(remitente, "No es tu turno. Espera a " + clienteActual.getNombreUsuario());
            return;
        }

        Jugador jugadorActual = jugadores.get(remitente.getClienteID());
        String comando = mensaje.trim().toLowerCase();

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

        broadcastMensaje(cliente.getNombreUsuario() + " jaló: " + carta);
        boolean sobrevivio = jugador.intentarJalarCarta(carta);

        if (!sobrevivio) {
            broadcastMensaje("¡BUST! " + cliente.getNombreUsuario() + " ha perdido la ronda con la carta repetida: " + carta);
            siguienteTurno();
        } else {
            enviarMensajePrivado(cliente, "Tu mano actual: " + jugador.obtenerCartasEnMano());
            enviarMensajePrivado(cliente, "¿Quieres /jalar otra o /parar?");
        }
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
            broadcastMensaje(" -> " + c.getNombreUsuario() + ": " + puntos + " puntos. (" + j.obtenerCartasEnMano() + ")");
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
            } catch (IOException e) { /* Ignorar desconexión */ }
        }
    }

    private void enviarMensajePrivado(UnCliente c, String msg) {
        try { 
            c.getSalida().writeUTF(msg); 
        } catch (IOException e) { /* Ignorar */ }
    }
    
    public boolean estaJuegoIniciado() { return juegoIniciado; }
}