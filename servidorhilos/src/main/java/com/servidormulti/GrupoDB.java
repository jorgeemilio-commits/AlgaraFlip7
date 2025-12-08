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
     * Saca un usuario de un grupo. 
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
        Connection conn = ConexionDB.conectar();
        if (conn == null) return "Error de conexión.";

        try (PreparedStatement pstmtMiembro = conn.prepareStatement(sqlDeleteMiembro)) {
            pstmtMiembro.setInt(1, grupoId);
            pstmtMiembro.setString(2, nombreUsuario);
            int filasAfectadas = pstmtMiembro.executeUpdate();

            if (filasAfectadas > 0) {
                return "Has salido del grupo '" + nombreGrupo + "'.";
            } else {
                return "No eras miembro del grupo '" + nombreGrupo + "'.";
            }
        } catch (SQLException e) {
            System.err.println("Error al salir de grupo: " + e.getMessage());
            return "Error interno al salir de grupo.";
        } finally {
            ConexionDB.cerrarConexion(conn);
        }
    }

    /**
     * Elimina un grupo de la base de datos.
     * Los miembros se eliminan automáticamente por CASCADE.
     */
    public boolean eliminarGrupo(String nombreGrupo) {
        if (nombreGrupo.equalsIgnoreCase("Todos")) {
            System.err.println("No se puede eliminar el grupo 'Todos'.");
            return false;
        }

        Integer grupoId = getGrupoId(nombreGrupo);
        if (grupoId == null) {
            return false;
        }

        String sql = "DELETE FROM grupos WHERE id = ?";
        Connection conn = ConexionDB.conectar();
        if (conn == null) return false;

        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, grupoId);
            int filasAfectadas = pstmt.executeUpdate();
            return filasAfectadas > 0;
        } catch (SQLException e) {
            System.err.println("Error al eliminar grupo: " + e.getMessage());
            return false;
        } finally {
            ConexionDB.cerrarConexion(conn);
        }
    }

    /**
     * Limpia todos los grupos excepto 'Todos'.
     * Se usa al iniciar el servidor.
     */
    public void limpiarTodasLasSalas() {
        String sql = "DELETE FROM grupos WHERE nombre != 'Todos'";
        Connection conn = ConexionDB.conectar();
        if (conn == null) return;

        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            int filasAfectadas = pstmt.executeUpdate();
            System.out.println("Todas las salas han sido limpiadas. Salas eliminadas: " + filasAfectadas);
        } catch (SQLException e) {
            System.err.println("Error al limpiar salas: " + e.getMessage());
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
}