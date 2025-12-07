package com.servidormulti.Flip7;

public class ManejadorAcciones {

    
     // Aplica el efecto de "Freeze".
    public String aplicarFreeze(Jugador objetivo) {
        if (objetivo == null) return "No hay objetivo válido.";
        
        // Si ya se plantó o perdió, no tiene caso congelarlo
        if (objetivo.sePlanto() || objetivo.tieneBUST()) {
            return "El objetivo ya terminó su turno, no se puede congelar.";
        }
        
        objetivo.congelar();
        return "El jugador " + objetivo.obtenerNombreUsuario() + " ha sido CONGELADO y forzado a hacer STAY.";
    }
}