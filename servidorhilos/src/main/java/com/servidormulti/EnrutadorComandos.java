package com.servidormulti;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

public class EnrutadorComandos {
    
    private final ManejadorComandos manejadorComandos;
    // [ELIMINADO: private final ManejadorJuegos manejadorJuegos;]
    private final ManejadorAutenticacion manejadorAutenticacion;

    public EnrutadorComandos(ManejadorComandos manejadorComandos, ManejadorAutenticacion manejadorAutenticacion) { 
        this.manejadorComandos = manejadorComandos;
        // [ELIMINADO: this.manejadorJuegos = manejadorJuegos;]
        this.manejadorAutenticacion = manejadorAutenticacion;
    }

    /**
     * Procesa el comando enviado por el cliente.
     */
    public void procesar(String mensaje, DataInputStream entrada, DataOutputStream salida, UnCliente cliente) throws IOException {
        
        String[] partes = mensaje.trim().split(" ", 3);
        String comando = partes[0].toLowerCase();
        
        // --- 1. COMANDOS DE AUTENTICACIÓN (Pueden ser usados por Invitados) ---
        if (comando.equals("/registrar")) {
            manejadorAutenticacion.manejarRegistro(entrada, salida, cliente);
            return;
        }
        if (comando.equals("/login")) {
            manejadorAutenticacion.manejarLogin(entrada, salida, cliente);
            return;
        }

        // Si el cliente no está logueado y el comando no es login/registrar, se le niega el acceso, 
        // a menos que sea un comando global como /ayuda o /conectados.
        
        // Si no está logueado (es invitado) y trata de usar cualquier otro comando, le negamos la mayoría.
        // Mantenemos /ayuda y /conectados disponibles.
        if (!cliente.estaLogueado() && 
            !comando.equals("/ayuda") && 
            !comando.equals("/conectados")) {
            
            salida.writeUTF("Error: Debes iniciar sesión (/login) o registrarte (/registrar) para usar este comando.");
            return;
        }

        // --- 2. COMANDOS DE JUEGO (Placeholder para Coup) ---
        // Estos comandos requieren que el usuario esté logueado, lo cual se verifica arriba.

        // Comando futuro para acciones de Coup
        if (comando.equals("/accion")) { 
            salida.writeUTF("Comando '/accion' reservado para la lógica de juego de Coup.");
            return;
        }
        
        // Comando futuro para salir del juego
        if (comando.equals("/salirjuego")) { 
            salida.writeUTF("Comando '/salirjuego' reservado para la lógica de juego de Coup.");
            return;
        }

        // Comando para iniciar una partida
        if (comando.equals("/jugar")) {
            salida.writeUTF("Comando '/jugar' reservado para retar a un oponente en Coup.");
            return;
        }
        
        // Comando para aceptar una partida
        if (comando.equals("/aceptar")) {
            salida.writeUTF("Comando '/aceptar' reservado para aceptar un reto en Coup.");
            return;
        }
        
        // --- 3. COMANDOS DE USUARIO Y GENERALES (Delegados a ManejadorComandos) ---
        if (comando.equals("/logout")) {
            manejadorComandos.manejarLogout(salida, cliente);
            return;
        }
        
        if (comando.equals("/conectados")) {
            manejadorComandos.manejarConectados(salida);
            return;
        }

        if (comando.equals("/ayuda")) {
            manejadorComandos.manejarAyuda(salida, cliente);
            return;
        }

        // [ELIMINADO: /block, /unblock, /rangos, /winrate, /creargrupo, /borrargrupo, /unirsegrupo, /salirgrupo]
        
        // --- 4. COMANDO DESCONOCIDO ---
        salida.writeUTF("Comando '" + comando + "' no reconocido. Usa /ayuda para ver la lista de comandos.");
    }
}