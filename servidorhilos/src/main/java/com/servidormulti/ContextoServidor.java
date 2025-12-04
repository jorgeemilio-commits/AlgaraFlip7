package com.servidormulti;

import java.util.Map;

public class ContextoServidor {

    private final Map<String, UnCliente> clientesConectados; 

    // Instancias Únicas
    private final GrupoDB grupoDB;
    private final ManejadorAutenticacion manejadorAutenticacion;
    private final ManejadorMensajes manejadorMensajes;
    private final ManejadorSalas manejadorSalas; 

    public ContextoServidor(Map<String, UnCliente> clientesConectados) {
        this.clientesConectados = clientesConectados; 

        //se inicializan las instancias
        
        this.grupoDB = new GrupoDB();

        this.manejadorAutenticacion = new ManejadorAutenticacion(clientesConectados); 
    
        this.manejadorMensajes = new ManejadorMensajes(
            clientesConectados, 
            this.grupoDB
        );
        
        this.manejadorSalas = new ManejadorSalas(this.grupoDB, this.manejadorMensajes);
    }

    public ManejadorMensajes getManejadorMensajes() { return manejadorMensajes; }
    public ManejadorAutenticacion getManejadorAutenticacion() { return manejadorAutenticacion; }
    public GrupoDB getGrupoDB() { return grupoDB; }
    public ManejadorSalas getManejadorSalas() { return manejadorSalas; } 
}