package com.servidormulti;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class Login {
    
    /**
     * Verifica las credenciales de un usuario consultando la base de datos.
     *
     * @param nombre El nombre del usuario.
     * @param password La contraseña del usuario.
     * @return true si las credenciales son correctas, false en caso contrario.
     */
    public boolean iniciarSesion(String nombre, String password) {
        String sql = "SELECT * FROM usuarios WHERE nombre = ? AND password = ?";
        Connection conn = ConexionDB.conectar();
        
        if (conn == null) {
            System.err.println("No se pudo conectar para iniciar sesión.");
            return false;
        }

        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, nombre);
            pstmt.setString(2, password);
            
            // Ejecutar la consulta
            ResultSet rs = pstmt.executeQuery();
            
            // Si rs.next() es true, significa que se encontró un registro
            return rs.next(); 

        } catch (SQLException e) {
            System.err.println("Error al verificar credenciales: " + e.getMessage());
            return false;
        } finally {
            ConexionDB.cerrarConexion(conn);
        }
    }
}