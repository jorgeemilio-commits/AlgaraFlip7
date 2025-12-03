package com.servidormulti;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class Registrar {

    public String registrarUsuario(String nombre, String password) {
        String sqlInsert = "INSERT INTO usuarios (nombre, password) VALUES (?, ?)";
        String sqlCheck = "SELECT count(*) FROM usuarios WHERE nombre = ?";
        Connection conn = ConexionDB.conectar(); 

        if (conn == null) {
            return "Error de conexión a la base de datos.";
        }

        try (PreparedStatement checkStmt = conn.prepareStatement(sqlCheck)) {
            // 1. Verificar si el nombre de usuario ya existe
            checkStmt.setString(1, nombre);
            ResultSet rs = checkStmt.executeQuery();
            if (rs.next() && rs.getInt(1) > 0) {
                return "El nombre de usuario ya está en uso.";
            }

            // 2. Insertar el nuevo usuario
            try (PreparedStatement insertStmt = conn.prepareStatement(sqlInsert)) {
                insertStmt.setString(1, nombre);
                insertStmt.setString(2, password);
                int filasAfectadas = insertStmt.executeUpdate();

                if (filasAfectadas > 0) {
                    return "Registro exitoso.";
                } else {
                    return "Error al registrar el usuario.";
                }
            }

        } catch (SQLException e) {
            // si el usuario ya existe, se captura la excepción y se informa al cliente
            if (e.getMessage().contains("UNIQUE constraint failed")) {
                return "El nombre de usuario ya está en uso.";
            }
            System.err.println("Error al registrar usuario: " + e.getMessage());
            return "Error interno al registrar usuario.";
        } finally {
            ConexionDB.cerrarConexion(conn);
        }
    }
}