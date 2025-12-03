// servidorhilos/src/main/java/com/servidormulti/ManejadorAutenticacion.java
package com.servidormulti;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Map; 
import java.text.Normalizer; 

public class ManejadorAutenticacion {

    private final Map<String, UnCliente> clientesConectados;

    public ManejadorAutenticacion(Map<String, UnCliente> clientes) {
        this.clientesConectados = clientes;
    }
    
    // NOTA: Los métodos manejarRegistro y manejarLogin (antiguos síncronos) han sido eliminados.

    // --- MÉTODOS DE REGISTRO POR MENÚ (3 PASOS) ---

    public void manejarRegistroPorMenu_Paso1(String nombre, DataOutputStream salida, UnCliente cliente) throws IOException {
        
        // 1. Validar nombre
        if (nombre.matches("[^a-zA-Z0-9áéíóúÁÉÍÓÚñÑ]+")) {
            salida.writeUTF("Error: El nombre de usuario contiene caracteres no permitidos. Solo se permiten letras, números y acentos.");
            cliente.mostrarMenuPrincipal(); 
            return;
        }
        
        // 2. Guardar y pasar al siguiente estado
        cliente.establecerNombreTemporal(nombre);
        cliente.establecerEstadoActual(EstadoMenu.REGISTRO_PEDIR_CONTRASENA); 
        salida.writeUTF("Introduce tu contraseña:");
    }
    
    public void manejarRegistroPorMenu_Paso2(String password, DataOutputStream salida, UnCliente cliente) throws IOException {
        
        // 1. Guardar y pasar al siguiente estado
        cliente.establecerContrasenaTemporal(password);
        cliente.establecerEstadoActual(EstadoMenu.REGISTRO_PEDIR_CONFIRMACION); 
        salida.writeUTF("Confirma tu contraseña:");
    }
    
    public void manejarRegistroPorMenu_Paso3(String confirmPassword, DataOutputStream salida, UnCliente cliente) throws IOException {
        String nombre = cliente.obtenerNombreTemporal();
        String password = cliente.obtenerContrasenaTemporal();
        
        // 1. Validar la confirmación
        if (!password.equals(confirmPassword)) {
            salida.writeUTF("Las contraseñas no coinciden. Registro cancelado.");
            cliente.limpiarTemporales();
            cliente.mostrarMenuPrincipal();
            return;
        }
        
        // 2. Ejecutar el registro real
        Registrar registrar = new Registrar();
        String resultado = registrar.registrarUsuario(nombre, password);
        salida.writeUTF(resultado);
        
        cliente.limpiarTemporales(); 
        
        // 3. Manejar el resultado
        if (resultado.contains("Registro exitoso")) {
            // Intentar login automático
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


    // --- MÉTODO DE LOGIN POR MENÚ (Llamado en el último paso de inicio de sesión o registro) ---
    
    /**
     * Maneja el login verificando credenciales, concurrencia y estableciendo el estado.
     * @param nombre Nombre de usuario.
     */
    public boolean manejarInicioSesionInternoPorMenu(String nombre, String password, DataOutputStream salida, UnCliente cliente) throws IOException {
        
        if (nombre == null || nombre.isEmpty()) {
            salida.writeUTF("Error: El nombre de usuario no fue proporcionado. Volviendo al menú principal.");
            return false;
        }

        // Normalizar también en el login para coherencia con el registro
        nombre = Normalizer.normalize(nombre, Normalizer.Form.NFC);

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
            // Se debe revisar por el ID ya que el nombre de invitado puede coincidir con un nombre real.
            if (!clienteActivo.clienteID.equals(cliente.clienteID) &&
                clienteActivo.getNombreUsuario().equalsIgnoreCase(nombre)) {
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