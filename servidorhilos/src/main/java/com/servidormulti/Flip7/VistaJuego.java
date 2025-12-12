package com.servidormulti.Flip7;

import com.servidormulti.UnCliente;
import java.io.IOException;
import java.util.List;
import java.util.Map;

public class VistaJuego {

    // --- MÉTODOS BASE DE COMUNICACIÓN ---

    public void broadcast(List<UnCliente> clientes, String mensaje) {
        for (UnCliente c : clientes) {
            enviar(c, mensaje);
        }
    }

    public void enviar(UnCliente c, String mensaje) {
        try {
            c.getSalida().writeUTF(mensaje);
        } catch (IOException e) { /* Ignorar errores de socket aquí */ }
    }

    // --- MÉTODOS DE ESTADO DEL JUEGO ---

    public void mostrarInicioPartida(List<UnCliente> clientes) {
        broadcast(clientes, "\n==========================================");
        broadcast(clientes, "--- ¡LA PARTIDA DE FLIP7 HA COMENZADO! ---");
        broadcast(clientes, "==========================================\n");
    }

    public void mostrarInicioRonda(List<UnCliente> clientes, int numeroRonda) { // Opcional pasar número
        broadcast(clientes, "\n--- INICIANDO SIGUIENTE RONDA ---");
    }

    public void anunciarTurno(List<UnCliente> clientes, UnCliente actual, Jugador jugadorActual) {
        if (clientes.isEmpty()) return;
        broadcast(clientes, "\n>>> Turno de: " + actual.getNombreUsuario() + " <<<");
        enviar(actual, "Tu mano actual: " + jugadorActual.obtenerCartasEnMano());
        enviar(actual, "Es tu turno. Escribe /jalar o /parar");
    }

    // --- ACCIONES Y EVENTOS ---

    public void mostrarCartaJalada(List<UnCliente> clientes, UnCliente actual, Carta carta) {
        broadcast(clientes, actual.getNombreUsuario() + " jaló: " + carta);
    }

    public void mostrarMensajeChat(List<UnCliente> clientes, String remitente, String mensaje) {
        broadcast(clientes, "<" + remitente + ">: " + mensaje);
    }

    public void mostrarBust(List<UnCliente> clientes, String nombre) {
        broadcast(clientes, "¡BUST! " + nombre + " ha perdido la ronda.");
    }

    public void mostrarSalvacionSecondChance(List<UnCliente> clientes, String nombre, Carta carta) {
        broadcast(clientes, " ¡" + nombre + " usó su SECOND CHANCE para salvarse! La carta " + carta + " fue descartada. ");
    }

    public void mostrarFlip7(List<UnCliente> clientes, String nombre) {
        broadcast(clientes, "\n¡FLIP 7! " + nombre + " consiguió 7 cartas únicas.");
    }

    public void mostrarPlantarse(List<UnCliente> clientes, String nombre, int puntos) {
        broadcast(clientes, nombre + " se ha PLANTADO con " + puntos + " puntos provisionales.");
    }

    // --- MENÚS DE CARTAS DE ACCIÓN ---

    public void mostrarMenuSeleccionObjetivo(UnCliente atacante, String nombreCarta, List<String> objetivos) {
        enviar(atacante, "¡Sacaste " + nombreCarta + "!");
        StringBuilder sb = new StringBuilder("--- JUGADORES DISPONIBLES ---\n");
        for (String nombre : objetivos) {
            sb.append(" - ").append(nombre).append("\n");
        }
        enviar(atacante, sb.toString());
        enviar(atacante, "Usa: /usar [Nombre]");
    }

    public void mostrarObtencionSecondChance(List<UnCliente> clientes, String nombre) {
        broadcast(clientes, nombre + " obtuvo una Second Chance.");
    }

    public void mostrarAccionFallida(UnCliente atacante, String error) {
        enviar(atacante, " Error: " + error + " Elige otro.");
    }

    public void mostrarAccionEjecutada(List<UnCliente> clientes, String accion, String objetivo) {
        broadcast(clientes, "ACCION " + accion + " ejecutada sobre " + objetivo);
    }

    // --- FLIP THREE ---

    public void mostrarEstadoFlipThree(List<UnCliente> clientes, String nombre, int restantes) {
        broadcast(clientes, nombre + " jalando carta de Flip Three... (" + restantes + " restantes)");
    }

    public void mostrarCartaFlipThree(List<UnCliente> clientes, Carta carta) {
        broadcast(clientes, "Salió: " + carta.toString());
    }

    // --- RESULTADOS ---

    public void mostrarResultadosRonda(List<UnCliente> clientes, String resumen) {
        broadcast(clientes, "\n--- FIN DE LA RONDA ---");
        broadcast(clientes, resumen);
    }

    public void mostrarFinJuego(List<UnCliente> clientes, String ganador, int puntos) {
        broadcast(clientes, "\n ¡JUEGO TERMINADO! ");
        broadcast(clientes, "El ganador es: " + ganador.toUpperCase());
        broadcast(clientes, "Puntuación Final: " + puntos);
        broadcast(clientes, "------------------------------------------");
        broadcast(clientes, "Escriban /listo para iniciar una partida nueva.");
    }

    public void mostrarEsperaNuevaRonda(List<UnCliente> clientes) {
        broadcast(clientes, "Nadie ha llegado a 200 puntos. La siguiente ronda comienza en 15 segundos...");
    }

    public void mostrarReportePuntuacion(UnCliente cliente, Map<String, Jugador> jugadores) {
        StringBuilder reporte = new StringBuilder("\n--- PUNTAJES ACTUALES ---\n");
        for (Jugador j : jugadores.values()) {
            reporte.append(j.obtenerNombreUsuario())
                   .append(": ")
                   .append(j.obtenerPuntuacionTotal())
                   .append(" pts\n");
        }
        reporte.append("-------------------------\n");
        enviar(cliente, reporte.toString());
    }
    
    // --- ERRORES Y VARIOS ---
    
    public void mostrarError(UnCliente c, String msg) {
        enviar(c, "Error: " + msg);
    }
    
    public void mostrarMensajeGenerico(List<UnCliente> clientes, String msg) {
        broadcast(clientes, msg);
    }
}