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
//comiteado
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