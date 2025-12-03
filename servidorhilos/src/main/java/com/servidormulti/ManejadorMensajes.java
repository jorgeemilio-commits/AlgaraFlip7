package com.servidormulti;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ManejadorMensajes {

    // PATRON_GRUPO se mantiene para identificar mensajes de grupo (ej: chat de partida).
    private static final Pattern PATRON_GRUPO = Pattern.compile("^#([\\w\\-]+)\\s+(.+)");

    private final Map<String, UnCliente> clientesConectados;
    private final GrupoDB grupoDB;
    // [ELIMINADO: MensajeDB y BloqueoDB]

    public ManejadorMensajes(Map<String, UnCliente> clientes, GrupoDB gdb) {
        this.clientesConectados = clientes;
        this.grupoDB = gdb;
    }

    private UnCliente buscarClienteConectado(String identificador) {
        // busca por ID numérico o por nombre de usuario.
        UnCliente cliente = clientesConectados.get(identificador);
        if (cliente != null) return cliente;
        for (UnCliente c : clientesConectados.values()) {
            if (c.getNombreUsuario().equalsIgnoreCase(identificador)) {
                return c;
            }
        }
        return null;
    }

    /**
     * Enruta el mensaje: lo difunde a los clientes correctos (sin guardar).
     */
    public void enrutarMensaje(UnCliente remitente, String mensaje) throws IOException {
        
        // --- 1. MENSAJE DE GRUPO (#grupo ...) ---
        Matcher matcherGrupo = PATRON_GRUPO.matcher(mensaje);
        if (matcherGrupo.find()) {
            if (!remitente.estaLogueado()) {
                remitente.salida.writeUTF("Error: Debes iniciar sesión para enviar mensajes a grupos.");
                return;
            }
            String nombreGrupo = matcherGrupo.group(1); 
            String contenido = matcherGrupo.group(2); 
            manejarMensajeGrupo(remitente, nombreGrupo, contenido);
            return;
        }

        // [ELIMINADO: MENSAJE PRIVADO (@usuario ...)]
        if (mensaje.startsWith("@")) {
            remitente.salida.writeUTF("Los mensajes privados están desactivados en esta versión.");
            return;
        }
        
        // --- 3. MENSAJE GENERAL A TODOS (Por defecto) ---
        manejarMensajeGrupo(remitente, "Todos", mensaje);
    }

    private void manejarMensajeGrupo(UnCliente remitente, String nombreGrupo, String contenido) throws IOException {
        String nombreRemitente = remitente.getNombreUsuario();
        
        Integer grupoId = grupoDB.getGrupoId(nombreGrupo);
        
        if (grupoId == null || grupoId == -1) {
            remitente.salida.writeUTF("Error: El grupo '" + nombreGrupo + "' no existe.");
            return;
        }
        
        // [ELIMINADO: Lógica de guardar mensaje en DB]

        List<String> miembros;
        
        if (nombreGrupo.equalsIgnoreCase("Todos")) {
            // Para el grupo 'Todos', enviamos a todos los conectados.
            miembros = new ArrayList<>(clientesConectados.keySet());
        } else {
            // Para grupos específicos (futuros chats de juego), usamos GrupoDB.
            miembros = grupoDB.getMiembrosGrupo(grupoId.intValue());
        }
        
        String msgFormateado = String.format("<%s> %s: %s", nombreGrupo, nombreRemitente, contenido);
        
        // 2. Broadcast a los miembros *conectados*
        for (String identificadorMiembro : miembros) {
            UnCliente clienteDestino = buscarClienteConectado(identificadorMiembro);
            
            // Si el cliente está conectado y no es el remitente
            if (clienteDestino != null && !clienteDestino.clienteID.equals(remitente.clienteID)) {
                
                // [ELIMINADO: BloqueoDB check]
                
                clienteDestino.salida.writeUTF(msgFormateado);
                
                // [ELIMINADO: Lógica de actualizar estado/mensajes vistos]
            }
        }
        
        // Confirmar el envío (mostrando el mensaje al remitente)
        remitente.salida.writeUTF(msgFormateado); 
    }
    
    // [ELIMINADO: manejarMensajePrivado]
}