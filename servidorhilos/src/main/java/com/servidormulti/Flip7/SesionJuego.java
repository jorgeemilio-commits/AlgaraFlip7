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