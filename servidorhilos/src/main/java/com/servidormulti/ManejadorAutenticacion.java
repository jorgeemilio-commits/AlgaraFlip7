package com.servidormulti;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Map; 
import java.text.Normalizer; // agregado

public class ManejadorAutenticacion {

    private final Map<String, UnCliente> clientesConectados;

    public ManejadorAutenticacion(Map<String, UnCliente> clientes) {
        this.clientesConectados = clientes;
    }

    public boolean manejarRegistro(DataInputStream entrada, DataOutputStream salida, UnCliente cliente) throws IOException {
        salida.writeUTF("Introduce tu nombre de usuario:");
        String nombre = entrada.readUTF();

        // Depuración: imprimir el nombre recibido
        System.out.println("Nombre recibido: " + nombre);

        // Validar el nombre prohibiendo caracteres especiales
        if (nombre.matches("[^a-zA-Z0-9áéíóúÁÉÍÓÚñÑ]+")) {
            salida.writeUTF("Error: El nombre de usuario contiene caracteres no permitidos. Solo se permiten letras, números y acentos.");
            return false;
        }
        
        salida.writeUTF("Introduce tu contraseña:");
        String password = entrada.readUTF();

        salida.writeUTF("Confirma tu contraseña:");
        String confirmPassword = entrada.readUTF();

        if (!password.equals(confirmPassword)) {
            salida.writeUTF("Las contraseñas no coinciden. Intenta de nuevo.");
            return false;
        }

        Registrar registrar = new Registrar();
        String resultado = registrar.registrarUsuario(nombre, password);
        salida.writeUTF(resultado);
        
        if (resultado.contains("Registro exitoso")) {
            if (cliente.manejarLoginInterno(nombre, password)) {
                salida.writeUTF("Registro exitoso e inicio de sesión automático. Tu nuevo nombre es: " + cliente.getNombreUsuario());
                return true;
            } else {
                salida.writeUTF("Registro exitoso, pero ocurrió un error al iniciar sesión automáticamente.");
            }
        }
        return false;
    }
    
    public boolean manejarLogin(DataInputStream entrada, DataOutputStream salida, UnCliente cliente) throws IOException {
        salida.writeUTF("Introduce tu nombre de usuario:");
        String nombre = entrada.readUTF();

        // Normalizar también en el login para coherencia con el registro
        nombre = Normalizer.normalize(nombre, Normalizer.Form.NFC);

        salida.writeUTF("Introduce tu contraseña:");
        String password = entrada.readUTF();

        // 1. Verificar si este cliente YA está logueado
        if (cliente.estaLogueado()) {
            salida.writeUTF("Error: Ya has iniciado sesión como '" + cliente.getNombreUsuario() + "'.");
            return false;
        }

        // 2. Verificar credenciales 
        Login login = new Login(); 
        if (!login.iniciarSesion(nombre, password)) {
            salida.writeUTF("Credenciales incorrectas. Intenta de nuevo.");
            return false;
        }

        // 3. Buscar si alguien ya usa ese nombre
        for (UnCliente clienteActivo : clientesConectados.values()) {
            if (clienteActivo.getNombreUsuario().equalsIgnoreCase(nombre)) {
                // Rechazar el login
                salida.writeUTF("Error: El usuario '" + nombre + "' ya está conectado en otra sesión.");
                return false;
            }
        }

        // 4. Proceder con el login
        if (cliente.manejarLoginInterno(nombre, password)) { 
            salida.writeUTF("Inicio de sesión exitoso. Tu nuevo nombre es: " + cliente.getNombreUsuario() + ". Ahora puedes enviar mensajes sin límite.");
            return true;
        } else {
            salida.writeUTF("Error interno al intentar iniciar sesión.");
            return false;
        }
    }
}