// servidorhilos/src/main/java/com/servidormulti/ContextoServidor.java
package com.servidormulti;

import java.util.Map;

public class ContextoServidor {

    private final Map<String, UnCliente> clientesConectados; 
    private final GrupoDB grupoDB;
    private final ManejadorAutenticacion manejadorAutenticacion;
    private final ManejadorMensajes manejadorMensajes;

    public ContextoServidor(Map<String, UnCliente> clientesConectados) {
        this.clientesConectados = clientesConectados; 

        this.grupoDB = new GrupoDB();
       
        this.manejadorAutenticacion = new ManejadorAutenticacion(clientesConectados); 
    
        this.manejadorMensajes = new ManejadorMensajes(
            clientesConectados, 
            this.grupoDB
        );
    }

    // --- Getters ---

    public ManejadorMensajes getManejadorMensajes() { return manejadorMensajes; }
    public ManejadorAutenticacion getManejadorAutenticacion() { return manejadorAutenticacion; }

}