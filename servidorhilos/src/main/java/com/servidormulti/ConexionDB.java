package com.servidormulti;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

public class ConexionDB {

    private static final String URL = "jdbc:sqlite:usuarios.db";

    public static Connection conectar() {
        Connection conn = null;
        try {
            Class.forName("org.sqlite.JDBC");
            
            org.sqlite.SQLiteConfig config = new org.sqlite.SQLiteConfig();
            config.enforceForeignKeys(true);
            
            conn = DriverManager.getConnection(URL, config.toProperties());
            
            crearTablas(conn); 
            inicializarDatosBase(conn); // Mantiene la creación del grupo "Todos"

        } catch (ClassNotFoundException e) {
            System.err.println("Error: Driver JDBC de SQLite no encontrado.");
            e.printStackTrace();
        } catch (SQLException e) {
            System.err.println("Error al conectar o crear la base de datos: " + e.getMessage());
        }
        return conn;
    }

    private static void crearTablas(Connection conn) {
        
        // Tabla de Usuarios
        String sqlUsuarios = "CREATE TABLE IF NOT EXISTS usuarios (" +
                     "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                     "nombre TEXT NOT NULL UNIQUE," +
                     "password TEXT NOT NULL" +
                     ");";
                     
        // Tabla de Grupos 
        String sqlGrupos = "CREATE TABLE IF NOT EXISTS grupos (" +
                           "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                           "nombre TEXT NOT NULL UNIQUE" +
                           ");";

        // Tabla de Miembros 
        String sqlGruposMiembros = "CREATE TABLE IF NOT EXISTS grupos_miembros (" +
                                 "grupo_id INTEGER NOT NULL," +
                                 "usuario_nombre TEXT NOT NULL," +
                                 "PRIMARY KEY (grupo_id, usuario_nombre)," +
                                 "FOREIGN KEY (grupo_id) REFERENCES grupos(id) ON DELETE CASCADE" +
                                 ");";

        try (Statement stmt = conn.createStatement()) {
            
            stmt.execute(sqlUsuarios);
            stmt.execute(sqlGrupos);
            stmt.execute(sqlGruposMiembros);
            
            System.out.println("Tablas (usuarios, grupos y miembros) verificadas o creadas.");
        } catch (SQLException e) {
            System.err.println("Error al crear las tablas: " + e.getMessage());
        }
    }

    /**
     * Asegura que el grupo "Todos" exista.
     */
    private static void inicializarDatosBase(Connection conn) {
        String sqlCheck = "SELECT COUNT(*) FROM grupos WHERE nombre = ?";
        String sqlInsert = "INSERT INTO grupos (nombre) VALUES (?)";

        try (PreparedStatement checkStmt = conn.prepareStatement(sqlCheck)) {
            checkStmt.setString(1, "Todos");
            ResultSet rs = checkStmt.executeQuery();
            if (rs.next() && rs.getInt(1) == 0) {
                try (PreparedStatement insertStmt = conn.prepareStatement(sqlInsert)) {
                    insertStmt.setString(1, "Todos");
                    insertStmt.executeUpdate();
                    System.out.println("Grupo 'Todos' inicializado en la base de datos.");
                }
            }
        } catch (SQLException e) {
            System.err.println("Error al inicializar datos base (Grupo 'Todos'): " + e.getMessage());
        }
    }

    public static void cerrarConexion(Connection conn) {
        try {
            if (conn != null) {
                conn.close();
            }
        } catch (SQLException ex) {
            System.err.println("Error al cerrar la conexión: " + ex.getMessage());
        }
    }
}