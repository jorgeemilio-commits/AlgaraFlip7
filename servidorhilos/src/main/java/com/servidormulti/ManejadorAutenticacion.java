package com.servidormulti;

import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Map;
import java.text.Normalizer;

public class ManejadorAutenticacion {

    private final Map<String, UnCliente> clientesConectados;

    public ManejadorAutenticacion(Map<String, UnCliente> clientes) {
        this.clientesConectados = clientes;
    }

    // --- MÉTODOS DE REGISTRO POR MENÚ ---

    public void manejarRegistroPorMenu_Paso1(String nombre, DataOutputStream salida, UnCliente cliente)
            throws IOException {
        // Verifica que sea un nombre válido
        if (nombre.matches("[^a-zA-Z0-9áéíóúÁÉÍÓÚñÑ]+")) {
            salida.writeUTF(
                    "Error: El nombre de usuario contiene caracteres no permitidos. Solo se permiten letras, números y acentos.");
            cliente.getManejadorMenu().mostrarMenuPrincipal(cliente, salida);
            return;
        }

        String nombreMinusculas = nombre.toLowerCase();

        if (nombreMinusculas.contains("/")) {
            salida.writeUTF("Error: El nombre de usuario no puede contener el símbolo de comando (/).");
            cliente.getManejadorMenu().mostrarMenuPrincipal(cliente, salida);
            return;
        }
        if (nombreMinusculas.equals("invitado")) {
            salida.writeUTF("Error: 'Invitado' es un nombre de usuario reservado.");
            cliente.getManejadorMenu().mostrarMenuPrincipal(cliente, salida);
            return;
        }
        // Guarda el nombre temporalmente y pide la contraseña
        cliente.establecerNombreTemporal(nombre);
        cliente.establecerEstadoActual(EstadoMenu.REGISTRO_PEDIR_CONTRASENA);
        salida.writeUTF("Introduce tu contraseña:");
    }

    public void manejarRegistroPorMenu_Paso2(String password, DataOutputStream salida, UnCliente cliente)
            throws IOException {
        // Guarda la contraseña temporalmente y pide confirmación
        cliente.establecerContrasenaTemporal(password);
        cliente.establecerEstadoActual(EstadoMenu.REGISTRO_PEDIR_CONFIRMACION);
        salida.writeUTF("Confirma tu contraseña:");
    }

    public boolean manejarRegistroPorMenu_Paso3(String confirmPassword, DataOutputStream salida, UnCliente cliente)
            throws IOException {
        String nombre = cliente.obtenerNombreTemporal();
        String password = cliente.obtenerContrasenaTemporal();

        // Verifica que las contraseñas coincidan
        if (!password.equals(confirmPassword)) {
            salida.writeUTF("Las contraseñas no coinciden. Registro cancelado.");
            cliente.limpiarTemporales();
            return false;
        }

        Registrar registrar = new Registrar();
        String resultado = registrar.registrarUsuario(nombre, password);
        salida.writeUTF(resultado);

        // Limpia los datos temporales
        cliente.limpiarTemporales();

        if (resultado.contains("Registro exitoso")) {
            // Intentar login automático
            if (manejarInicioSesionInternoPorMenu(nombre, password, salida, cliente)) {
                salida.writeUTF("Registro exitoso e inicio de sesión automático. Tu nuevo nombre es: "
                        + cliente.getNombreUsuario());
                return true;
            } else {
                salida.writeUTF("Registro exitoso, pero ocurrió un error al iniciar sesión automáticamente.");
                return false;
            }
        } else {
            return false;
        }
    }

    // --- MÉTODO DE LOGIN POR MENÚ ---

    public boolean manejarInicioSesionInternoPorMenu(String nombre, String password, DataOutputStream salida,
            UnCliente cliente) throws IOException {

        // Verifica que el nombre no esté vacío
        if (nombre == null || nombre.isEmpty()) {
            salida.writeUTF("Error: El nombre de usuario no fue proporcionado.");
            return false;
        }

        String nombreMinusculas = nombre.toLowerCase();

        if (nombreMinusculas.contains("/")) {
            salida.writeUTF("Error: El nombre no puede contener el símbolo de comando (/).");
            return false;
        }
        if (nombreMinusculas.equals("invitado")) {
            salida.writeUTF("Error: 'Invitado' es un nombre reservado y no se puede usar para iniciar sesión.");
            return false;
        }
        nombre = Normalizer.normalize(nombre, Normalizer.Form.NFC);

        // Verifica si el cliente ya está logueado
        if (cliente.estaLogueado()) {
            salida.writeUTF("Error: Ya has iniciado sesión como '" + cliente.getNombreUsuario() + "'.");
            return false;
        }

        // Verifica las credenciales con la base de datos
        Login login = new Login();
        if (!login.iniciarSesion(nombre, password)) {
            salida.writeUTF("Credenciales incorrectas. Intenta de nuevo.");
            return false;
        }

        // Verifica si ya está logeado en otra sesión
        for (UnCliente clienteActivo : clientesConectados.values()) {
            if (!clienteActivo.clienteID.equals(cliente.clienteID) &&
                    clienteActivo.getNombreUsuario().equalsIgnoreCase(nombre)) {
                salida.writeUTF("Error: El usuario '" + nombre + "' ya está conectado en otra sesión.");
                return false;
            }
        }

        // Iniciar sesión
        if (cliente.manejarLoginInterno(nombre, password)) {
            salida.writeUTF("Inicio de sesión exitoso. Tu nuevo nombre es: " + cliente.getNombreUsuario() + ".");
            return true;
        } else {
            salida.writeUTF("Error interno al intentar iniciar sesión.");
            return false;
        }
    }
}