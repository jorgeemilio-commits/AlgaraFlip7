package com.servidormulti.Flip7;

import com.servidormulti.ConexionDB;
import com.servidormulti.UnCliente;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class GuardadoPartida {

    public boolean guardarYTerminar(List<UnCliente> clientes, Map<String, Jugador> jugadores, int turnoActual, VistaJuego vista) {
        
        vista.mostrarMensajeGenerico(clientes, "Todos aceptaron. Guardando partida en base de datos...");

        Connection conn = ConexionDB.conectar();
        if (conn == null) {
            vista.mostrarMensajeGenerico(clientes, "Error crítico: No hay conexión a BD.");
            return false;
        }

        try {
            conn.setAutoCommit(false); // Transacción segura

            // 1. Guardar la partida (Sala y Turno)
            String sqlPartida = "INSERT INTO partidas_guardadas (sala, turno_actual) VALUES (?, ?)";
            int partidaId = -1;
            
            // Asumimos que todos están en la misma sala, tomamos el nombre del primero
            String nombreSala = clientes.get(0).obtenerSalaActual();

            try (PreparedStatement pstmt = conn.prepareStatement(sqlPartida, Statement.RETURN_GENERATED_KEYS)) {
                pstmt.setString(1, nombreSala);
                pstmt.setInt(2, turnoActual);
                pstmt.executeUpdate();

                ResultSet rs = pstmt.getGeneratedKeys();
                if (rs.next()) {
                    partidaId = rs.getInt(1);
                }
            }

            // 2. Guardar a cada jugador
            String sqlJugador = "INSERT INTO jugadores_guardados (partida_id, nombre_usuario, puntuacion, tiene_second_chance, cartas_mano) VALUES (?, ?, ?, ?, ?)";
            
            try (PreparedStatement pstmt = conn.prepareStatement(sqlJugador)) {
                for (UnCliente c : clientes) {
                    Jugador j = jugadores.get(c.getClienteID());
                    
                    // Convertir cartas a texto (Ej: "5,x2,Freeze")
                    String cartasString = j.obtenerCartasEnMano().stream()
                                           .map(Carta::toString)
                                           .collect(Collectors.joining(","));

                    pstmt.setInt(1, partidaId);
                    pstmt.setString(2, j.obtenerNombreUsuario());
                    pstmt.setInt(3, j.obtenerPuntuacionTotal());
                    pstmt.setInt(4, j.tieneSecondChance() ? 1 : 0);
                    pstmt.setString(5, cartasString);
                    pstmt.addBatch();
                }
                pstmt.executeBatch();
            }

            conn.commit(); // Confirmar cambios
            vista.mostrarMensajeGenerico(clientes, "¡Partida guardada exitosamente! Cerrando sala...");

            // 3. Gestionar la salida de los jugadores (resetear su estado visual)
            // Hacemos copia para evitar errores de concurrencia al iterar
            List<UnCliente> copiaClientes = new ArrayList<>(clientes);
            for (UnCliente c : copiaClientes) {
                c.getManejadorMenu().mostrarMenuSalaPrincipal(c, c.getSalida());
                c.establecerSalaActual(null);
            }

            return true; // Éxito

        } catch (Exception e) {
            try { conn.rollback(); } catch (SQLException ex) {} // Deshacer si falla
            vista.mostrarMensajeGenerico(clientes, "Error al guardar en BD: " + e.getMessage());
            e.printStackTrace();
            return false; // Fallo
        } finally {
            ConexionDB.cerrarConexion(conn);
        }
    }
}