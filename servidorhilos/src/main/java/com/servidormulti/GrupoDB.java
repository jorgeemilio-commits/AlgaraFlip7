package com.servidormulti;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

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

    // Se eliminó crearGrupo y borrarGrupo.

    /**
     * Une un usuario a un grupo. (Necesario para chats de partida y para "Todos")
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
     * Obtiene una lista de todos los miembros de un grupo. (Necesario para ManejadorMensajes)
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
}