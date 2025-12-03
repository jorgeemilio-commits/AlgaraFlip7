package com.servidormulti;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Map; 
import java.text.Normalizer; 
import com.servidormulti.EstadoMenu;

public class ManejadorAutenticacion {

    private final Map<String, UnCliente> clientesConectados;

    public ManejadorAutenticacion(Map<String, UnCliente> clientes) {
        this.clientesConectados = clientes;
    }
    
    // --- MÉTODOS DE REGISTRO POR MENÚ ---

    public void manejarRegistroPorMenu_Paso1(String nombre, DataOutputStream salida, UnCliente cliente) throws IOException {
        
        // Validar nombre de usuario
        if (nombre.matches("[^a-zA-Z0-9áéíóúÁÉÍÓÚñÑ]+")) {
            salida.writeUTF("Error: El nombre de usuario contiene caracteres no permitidos. Solo se permiten letras, números y acentos.");
            cliente.mostrarMenuPrincipal(); 
            return;
        }
        
        // Lo guarda temporalmente
        cliente.establecerNombreTemporal(nombre);
        cliente.establecerEstadoActual(EstadoMenu.REGISTRO_PEDIR_CONTRASENA); 
        salida.writeUTF("Introduce tu contraseña:");
    }
    
    public void manejarRegistroPorMenu_Paso2(String password, DataOutputStream salida, UnCliente cliente) throws IOException {
        
        // Lo guarda temporalmente
        cliente.establecerContrasenaTemporal(password);
        cliente.establecerEstadoActual(EstadoMenu.REGISTRO_PEDIR_CONFIRMACION); 
        salida.writeUTF("Confirma tu contraseña:");
    }
    
    public void manejarRegistroPorMenu_Paso3(String confirmPassword, DataOutputStream salida, UnCliente cliente) throws IOException {
        String nombre = cliente.obtenerNombreTemporal();
        String password = cliente.obtenerContrasenaTemporal();
        
        // Validar confirmación de contraseña
        if (!password.equals(confirmPassword)) {
            salida.writeUTF("Las contraseñas no coinciden. Registro cancelado.");
            cliente.limpiarTemporales();
            cliente.mostrarMenuPrincipal();
            return;
        }
        
        // Proceder con el registro del usuario
        Registrar registrar = new Registrar();
        String resultado = registrar.registrarUsuario(nombre, password);
        salida.writeUTF(resultado);
        
        // Limpiar datos temporales
        cliente.limpiarTemporales(); 
        
        if (resultado.contains("Registro exitoso")) {
            if (manejarInicioSesionInternoPorMenu(nombre, password, salida, cliente)) { 
                salida.writeUTF("Registro exitoso e inicio de sesión automático. Tu nuevo nombre es: " + cliente.getNombreUsuario());
                cliente.mostrarMenuChat();
            } else {
                salida.writeUTF("Registro exitoso, pero ocurrió un error al iniciar sesión automáticamente.");
                cliente.mostrarMenuPrincipal();
            }
        } else {
            cliente.mostrarMenuPrincipal();
        }
    }


    // --- MÉTODO DE LOGIN POR MENÚ ---
    
    public boolean manejarInicioSesionInternoPorMenu(String nombre, String password, DataOutputStream salida, UnCliente cliente) throws IOException {
        
        // No se dio el nombre
        if (nombre == null || nombre.isEmpty()) {
            salida.writeUTF("Error: El nombre de usuario no fue proporcionado. Volviendo al menú principal.");
            return false;
        }

        // Normalizar nombre de usuario
        nombre = Normalizer.normalize(nombre, Normalizer.Form.NFC);

        // Verificar si el cliente ya está logueado
        if (cliente.estaLogueado()) {
            salida.writeUTF("Error: Ya has iniciado sesión como '" + cliente.getNombreUsuario() + "'.");
            return false;
        }

        // Verificar credenciales en la base de datos
        Login login = new Login(); 
        if (!login.iniciarSesion(nombre, password)) {
            salida.writeUTF("Credenciales incorrectas. Intenta de nuevo.");
            return false;
        }

        // Verificar si el usuario ya está conectado en otra sesión
        for (UnCliente clienteActivo : clientesConectados.values()) {
            if (!clienteActivo.clienteID.equals(cliente.clienteID) &&
                clienteActivo.getNombreUsuario().equalsIgnoreCase(nombre)) {
                salida.writeUTF("Error: El usuario '" + nombre + "' ya está conectado en otra sesión.");
                return false;
            }
        }

        // Manejar el inicio de sesión
        if (cliente.manejarLoginInterno(nombre, password)) { 
            salida.writeUTF("Inicio de sesión exitoso. Tu nuevo nombre es: " + cliente.getNombreUsuario() + ". Ahora puedes enviar mensajes sin límite.");
            return true;
        } else {
            salida.writeUTF("Error interno al intentar iniciar sesión.");
            return false;
        }
    }
}