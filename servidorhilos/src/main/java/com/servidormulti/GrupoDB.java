package com.servidormulti;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class GrupoDB {

    /**
     * Obtiene el ID numérico de un grupo a partir de su nombre.
     */
    public Integer getGrupoId(String nombreGrupo) {
        String sql = "SELECT id FROM grupos WHERE nombre = ?";
        Connection conn = ConexionDB.conectar();
        if (conn == null) return null;

        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, nombreGrupo);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                return rs.getInt("id");
            }
        } catch (SQLException e) {
            System.err.println("Error al obtener ID de grupo: " + e.getMessage());
        } finally {
            ConexionDB.cerrarConexion(conn);
        }
        return null;
    }

    /**
     * Crea un nuevo grupo en la base de datos.
     */
    public String crearGrupo(String nombreGrupo) {
        if (getGrupoId(nombreGrupo) != null) {
            return "Error: El grupo '" + nombreGrupo + "' ya existe.";
        }

        String sql = "INSERT INTO grupos (nombre) VALUES (?)";
        Connection conn = ConexionDB.conectar();
        if (conn == null) return "Error de conexión.";

        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, nombreGrupo);
            int filas = pstmt.executeUpdate();
            if (filas > 0) {
                return "Sala '" + nombreGrupo + "' creada exitosamente.";
            } else {
                return "Error al crear la sala.";
            }
        } catch (SQLException e) {
            System.err.println("Error al crear grupo: " + e.getMessage());
            return "Error interno al crear grupo.";
        } finally {
            ConexionDB.cerrarConexion(conn);
        }
    }

    /**
     * Une un usuario a un grupo.
     */
    public String unirseGrupo(String nombreGrupo, String nombreUsuario) {
        Integer grupoId = getGrupoId(nombreGrupo);
        if (grupoId == null) {
            return "Error: El grupo '" + nombreGrupo + "' no existe.";
        }
        
        String sqlInsert = "INSERT OR IGNORE INTO grupos_miembros (grupo_id, usuario_nombre) VALUES (?, ?)";
        Connection conn = ConexionDB.conectar();
        if (conn == null) return "Error de conexión.";

        try (PreparedStatement pstmt = conn.prepareStatement(sqlInsert)) {
            pstmt.setInt(1, grupoId);
            pstmt.setString(2, nombreUsuario);
            int filasAfectadas = pstmt.executeUpdate();

            if (filasAfectadas > 0) {
                return "Te has unido al grupo '" + nombreGrupo + "'.";
            } else {
                return "Ya eras miembro del grupo '" + nombreGrupo + "'.";
            }
        } catch (SQLException e) {
            System.err.println("Error al unirse a grupo: " + e.getMessage());
            return "Error interno al unirse a grupo.";
        } finally {
            ConexionDB.cerrarConexion(conn);
        }
    }

    /**
     * Saca un usuario de un grupo y BORRA LA SALA si queda vacía.
     */
    public String salirGrupo(String nombreGrupo, String nombreUsuario) {
        if (nombreGrupo.equalsIgnoreCase("Todos")) {
            return "Error: No puedes salir del grupo 'Todos'.";
        }

        Integer grupoId = getGrupoId(nombreGrupo);
        if (grupoId == null) {
            return "Error: El grupo '" + nombreGrupo + "' no existe.";
        }

        String sqlDeleteMiembro = "DELETE FROM grupos_miembros WHERE grupo_id = ? AND usuario_nombre = ?";
        // Consulta para verificar cuántos quedan
        String sqlCountMiembros = "SELECT COUNT(*) FROM grupos_miembros WHERE grupo_id = ?";
        // Consulta para borrar la sala
        String sqlDeleteGrupo = "DELETE FROM grupos WHERE id = ?";

        Connection conn = ConexionDB.conectar();
        if (conn == null) return "Error de conexión.";

        try {
            // Eliminar al usuario del grupo
            try (PreparedStatement pstmtMiembro = conn.prepareStatement(sqlDeleteMiembro)) {
                pstmtMiembro.setInt(1, grupoId);
                pstmtMiembro.setString(2, nombreUsuario);
                int filasAfectadas = pstmtMiembro.executeUpdate();

                if (filasAfectadas > 0) {
                    // Si salió con éxito, verificamos si la sala quedó vacía
                    try (PreparedStatement pstmtCount = conn.prepareStatement(sqlCountMiembros)) {
                        pstmtCount.setInt(1, grupoId);
                        ResultSet rs = pstmtCount.executeQuery();
                        
                        if (rs.next() && rs.getInt(1) == 0) {
                            // Si hay 0 miembros, borramos la sala de la tabla 'grupos'
                            try (PreparedStatement pstmtDeleteGrupo = conn.prepareStatement(sqlDeleteGrupo)) {
                                pstmtDeleteGrupo.setInt(1, grupoId);
                                pstmtDeleteGrupo.executeUpdate();
                                System.out.println("Sala '" + nombreGrupo + "' eliminada automáticamente por estar vacía.");
                            }
                        }
                    }
                    return "Has salido del grupo '" + nombreGrupo + "'.";
                } else {
                    return "No eras miembro del grupo '" + nombreGrupo + "'.";
                }
            }
        } catch (SQLException e) {
            System.err.println("Error al salir de grupo: " + e.getMessage());
            return "Error interno al salir de grupo.";
        } finally {
            ConexionDB.cerrarConexion(conn);
        }
    }

    /**
     * Obtiene una lista de todos los miembros de un grupo.
     */
    public List<String> getMiembrosGrupo(int grupoId) {
        List<String> miembros = new ArrayList<>();
        String sql = "SELECT usuario_nombre FROM grupos_miembros WHERE grupo_id = ?";
        Connection conn = ConexionDB.conectar();
        if (conn == null) return miembros;
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, grupoId);
            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) {
                miembros.add(rs.getString("usuario_nombre"));
            }
        } catch (SQLException e) {
            System.err.println("Error al obtener miembros de grupo: " + e.getMessage());
        } finally {
            ConexionDB.cerrarConexion(conn);
        }
        return miembros;
    }

    /**
     * Obtiene un mapa con el nombre de la sala y la cantidad actual de jugadores.
     * Filtra automáticamente las salas que ya tienen 6 o más jugadores.
     */
    public Map<String, Integer> obtenerSalasDisponibles() {
        Map<String, Integer> salas = new HashMap<>();
        String sql = "SELECT g.nombre, COUNT(gm.usuario_nombre) as cantidad " +
                     "FROM grupos g " +
                     "LEFT JOIN grupos_miembros gm ON g.id = gm.grupo_id " +
                     "WHERE g.nombre <> 'Todos' " + 
                     "GROUP BY g.id, g.nombre " +
                     "HAVING cantidad < 6";

        Connection conn = ConexionDB.conectar();
        if (conn == null) return salas;

        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) {
                String nombre = rs.getString("nombre");
                int cantidad = rs.getInt("cantidad");
                salas.put(nombre, cantidad);
            }
        } catch (SQLException e) {
            System.err.println("Error al obtener salas disponibles: " + e.getMessage());
        } finally {
            ConexionDB.cerrarConexion(conn);
        }
        return salas;
    }
    
    /**
     * Obtiene una lista con los nombres de todas las salas disponibles.
     */
    public List<String> obtenerNombresDeGrupos() {
        return new ArrayList<>(obtenerSalasDisponibles().keySet());
    }

    
    // --- MÉTODOS PARA CARGAR PARTIDAS ---

    public Map<Integer, String> obtenerPartidasGuardadas(String nombreUsuario) {
        Map<Integer, String> partidas = new HashMap<>();
        // Buscamos partidas donde el usuario esté registrado en la tabla de guardado
        String sql = "SELECT p.id, p.sala FROM partidas_guardadas p " +
                     "JOIN jugadores_guardados j ON p.id = j.partida_id " +
                     "WHERE j.nombre_usuario = ?";
        
        Connection conn = ConexionDB.conectar();
        if (conn == null) return partidas;

        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, nombreUsuario);
            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) {
                partidas.put(rs.getInt("id"), rs.getString("sala"));
            }
        } catch (SQLException e) {
            System.err.println("Error al obtener partidas guardadas: " + e.getMessage());
        } finally {
            ConexionDB.cerrarConexion(conn);
        }
        return partidas;
    }

    // Restaura la sala en la tabla 'grupos' si fue eliminada al vaciarse
    public void asegurarSalaExiste(String nombreSala) {
        if (getGrupoId(nombreSala) == null) {
            crearGrupo(nombreSala); // Reutilizamos el método de crear
        }
    }

    // Obtiene el ID de partida guardada asociado a una sala (para saber si cargar o iniciar nueva)
    public Integer obtenerIdPartidaPorSala(String nombreSala) {
        String sql = "SELECT id FROM partidas_guardadas WHERE sala = ? ORDER BY id DESC LIMIT 1";
        Connection conn = ConexionDB.conectar();
        if (conn == null) return null;
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, nombreSala);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) return rs.getInt("id");
        } catch (SQLException e) { e.printStackTrace(); }
        finally { ConexionDB.cerrarConexion(conn); }
        return null;
    }

    // Obtiene el turno guardado
    public int obtenerTurnoGuardado(int partidaId) {
        String sql = "SELECT turno_actual FROM partidas_guardadas WHERE id = ?";
        Connection conn = ConexionDB.conectar();
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, partidaId);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) return rs.getInt("turno_actual");
        } catch (SQLException e) { e.printStackTrace(); }
        finally { ConexionDB.cerrarConexion(conn); }
        return 0;
    }

    // Estructura auxiliar para devolver datos del jugador
    public static class DatosJugadorGuardado {
        public String nombre;
        public int puntuacion;
        public boolean secondChance;
        public String cartas;
    }

    public List<DatosJugadorGuardado> cargarJugadoresDePartida(int partidaId) {
        List<DatosJugadorGuardado> lista = new ArrayList<>();
        String sql = "SELECT nombre_usuario, puntuacion, tiene_second_chance, cartas_mano FROM jugadores_guardados WHERE partida_id = ?";
        Connection conn = ConexionDB.conectar();
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, partidaId);
            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) {
                DatosJugadorGuardado d = new DatosJugadorGuardado();
                d.nombre = rs.getString("nombre_usuario");
                d.puntuacion = rs.getInt("puntuacion");
                d.secondChance = rs.getInt("tiene_second_chance") == 1;
                d.cartas = rs.getString("cartas_mano");
                lista.add(d);
            }
        } catch (SQLException e) { e.printStackTrace(); }
        finally { ConexionDB.cerrarConexion(conn); }
        return lista;
    }
    
    // Método para borrar la partida guardada una vez que se reanuda con éxito (opcional, para limpieza)
    public void eliminarPartidaGuardada(int partidaId) {
        String sql = "DELETE FROM partidas_guardadas WHERE id = ?";
        String sqlJ = "DELETE FROM jugadores_guardados WHERE partida_id = ?";
        Connection conn = ConexionDB.conectar();
        try {
            PreparedStatement p1 = conn.prepareStatement(sqlJ);
            p1.setInt(1, partidaId);
            p1.executeUpdate();
            
            PreparedStatement p2 = conn.prepareStatement(sql);
            p2.setInt(1, partidaId);
            p2.executeUpdate();
        } catch(SQLException e) { e.printStackTrace(); }
        finally { ConexionDB.cerrarConexion(conn); }
    }
}