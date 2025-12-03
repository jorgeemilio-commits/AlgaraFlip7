package com.servidormulti;

import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Map; 
// Se elimina la dependencia de ManejadorAccionesGrupo, ManejadorRangos, ManejadorWinrate, BloqueoDB, MensajeDB

public class ManejadorComandos {
    
    // --- MANEJADORES DELEGADOS ---
    // [ELIMINADO: ManejadorRangos, ManejadorWinrate, ManejadorAccionesGrupo]

    // --- OBJETOS DB ---
    private final GrupoDB grupoDB; // Mantenido para futura gestión de partidas
    // [ELIMINADO: BloqueoDB, MensajeDB]

    private final Map<String, UnCliente> clientesConectados;

    public ManejadorComandos(GrupoDB grupoDB, Map<String, UnCliente> clientes) {
        // [ELIMINADO: mr, mw, mag, bdb, mdb]
        this.grupoDB = grupoDB;
        this.clientesConectados = clientes; 
    }
    
    // [ELIMINADO: existeUsuarioDB, ya que dependía de MensajeDB]

    public void manejarLogout(DataOutputStream salida, UnCliente cliente) throws IOException {
        if (!cliente.estaLogueado()) {
            salida.writeUTF("Ya estás desconectado. Tu nombre es: " + cliente.getNombreUsuario());
            return;
        }
        cliente.manejarLogoutInterno();
        salida.writeUTF("Has cerrado sesión. Tu nombre es ahora '" + cliente.getNombreUsuario() + "'.");
    }

    // [ELIMINADO: manejarBloqueo]
    // [ELIMINADO: manejarDesbloqueo]
    // [ELIMINADO: manejarRangos]
    // [ELIMINADO: manejarWinrate]
    
    // -- Conectados --
    public void manejarConectados(DataOutputStream salida) throws IOException {
        // Mostrar usuarios conectados, incluyendo invitados
        StringBuilder lista = new StringBuilder("--- Usuarios Conectados ---\n");
        int contador = 0;
        for (UnCliente cliente : clientesConectados.values()) {
            lista.append("- ").append(cliente.getNombreUsuario());
            if (!cliente.estaLogueado()) {
                lista.append(" (Invitado)"); // Cambiado el formato
            }
            lista.append("\n");
            contador++;
        }

        if (contador == 0) {
            salida.writeUTF("No hay usuarios conectados en este momento.");
        } else {
            lista.append("Total: ").append(contador);
            salida.writeUTF(lista.toString());
        }
    }

    // ---  (Grupos - Solo se mantienen los comandos básicos de la interfaz para el futuro) ---
    // NOTA: Estos métodos ahora están vacíos, ya que la lógica de grupo estaba en ManejadorAccionesGrupo, que fue eliminado.
    // Solo sirven como placeholders para el EnrutadorComandos.
    
    public void manejarCrearGrupo(String[] partes, DataOutputStream salida, UnCliente cliente) throws IOException {
        salida.writeUTF("Comando temporalmente desactivado para simplificación.");
    }
    
    public void manejarBorrarGrupo(String[] partes, DataOutputStream salida, UnCliente cliente) throws IOException {
        salida.writeUTF("Comando temporalmente desactivado para simplificación.");
    }
    
    public void manejarUnirseGrupo(String[] partes, DataOutputStream salida, UnCliente cliente) throws IOException {
        // Lógica de unirse a "Todos" se mueve a UnCliente.manejarLoginInterno
        salida.writeUTF("Comando temporalmente desactivado para simplificación.");
    }

    public void manejarSalirGrupo(String[] partes, DataOutputStream salida, UnCliente cliente) throws IOException {
        salida.writeUTF("Comando temporalmente desactivado para simplificación.");
    }

    public void manejarAyuda(DataOutputStream salida, UnCliente cliente) throws IOException {
        StringBuilder ayuda = new StringBuilder("--- Lista de Comandos Disponibles ---\n");
        

    // -- comandos de ayuda especificados --
    
        // Comandos de Autenticación
        ayuda.append("--- Autenticación ---\n");
        ayuda.append("  /login         - Inicia sesión con tu cuenta.\n");
        ayuda.append("  /registrar     - Crea una nueva cuenta.\n");
        ayuda.append("  /logout        - Cierra tu sesión actual.\n");

        // Comandos Sociales y de Grupos (Mantenidos como referencia futura)
        ayuda.append("--- Grupos (Futuro Coup) ---\n");
        ayuda.append("  /conectados    - Muestra la lista de usuarios conectados.\n");
        // Los comandos de grupo y juego se mantienen como marcadores de posición:
        ayuda.append("  /jugar <oponente>     - Reta a un jugador (a ser implementado para Coup).\n");
        ayuda.append("  /aceptar <retador>    - Acepta una invitación (a ser implementado para Coup).\n");
        
        // Comandos Contextuales (Solo si está en juego)
        if (cliente.estaEnJuego()) {
            ayuda.append("--- Comandos de Partida (Activos) ---\n");
            ayuda.append("  /accion <ID> ... - Realiza una acción de Coup.\n");
            ayuda.append("  /salirjuego <ID> - Abandona la partida en curso.\n");
        }

        salida.writeUTF(ayuda.toString());
    }
}