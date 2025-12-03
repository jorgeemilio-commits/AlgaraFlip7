package com.servidormulti;

import java.util.Map;

// Se eliminan las referencias a ManejadorRangos, ManejadorJuegos, ManejadorAutenticacion, ManejadorSincronizacion, ManejadorAccionesGrupo, BloqueoDB, MensajeDB

public class ContextoServidor {

    // --- CAMPOS PRIVADOS ---
    
    private final Map<String, UnCliente> clientesConectados; 

    // Instancias Únicas de Servicios (Singleton)
    private final GrupoDB grupoDB;
    // [ELIMINADO: MensajeDB, BloqueoDB, ManejadorJuegos, ManejadorSincronizacion, ManejadorAccionesGrupo, ManejadorRangos, ManejadorWinrate]
    
    private final ManejadorComandos manejadorComandos;
    private final ManejadorAutenticacion manejadorAutenticacion;
    private final ManejadorMensajes manejadorMensajes;
    private final EnrutadorComandos enrutadorComandos;

    public ContextoServidor(Map<String, UnCliente> clientesConectados) {
        this.clientesConectados = clientesConectados; 

        // 1. Inicializar objetos DB
        this.grupoDB = new GrupoDB();

        // 2. Inicializar manejadores principales
        this.manejadorComandos = new ManejadorComandos(
            this.grupoDB, 
            clientesConectados 
        );
       
        this.manejadorAutenticacion = new ManejadorAutenticacion(clientesConectados); 
    
        this.manejadorMensajes = new ManejadorMensajes(
            clientesConectados, 
            this.grupoDB
        );
        
        // 3. Inicializar el enrutador
        this.enrutadorComandos = new EnrutadorComandos(
            this.manejadorComandos, 
            this.manejadorAutenticacion
        );
    }

    // --- Getters ---

    public ManejadorMensajes getManejadorMensajes() { return manejadorMensajes; }
    // [ELIMINADO: getManejadorSincronizacion]
    public EnrutadorComandos getEnrutadorComandos() { return enrutadorComandos; }
}