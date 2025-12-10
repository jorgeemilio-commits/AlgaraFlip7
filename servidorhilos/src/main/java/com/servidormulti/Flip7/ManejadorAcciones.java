package com.servidormulti.Flip7;

public class ManejadorAcciones {

    // Aplica el efecto de "Freeze".
    public String aplicarFreeze(Jugador objetivo) {
        if (objetivo == null)
            return "No hay objetivo válido.";

        // Si ya se plantó o perdió, no tiene caso congelarlo
        if (objetivo.sePlanto() || objetivo.tieneBUST()) {
            return "El objetivo ya terminó su turno, no se puede congelar.";
        }

        objetivo.congelar();
        return "El jugador " + objetivo.obtenerNombreUsuario() + " ha sido CONGELADO y forzado a hacer STAY.";
    }

    //nuevo metodo flip three corregido
    public Carta jalarUnaCartaFlipThree(Jugador objetivo, Baraja baraja) {
        if (objetivo == null || objetivo.sePlanto() || objetivo.tieneBUST())
            return null;

        Carta c = baraja.jalarCarta();

        // Manejo de mazo vacío
        if (c == null) {
            baraja.reiniciarBaraja();
            c = baraja.jalarCarta();
        }
        if (c == null)
            return null;

        // Si es una acción, la devolvemos inmediatamente para que SesionJuego la
        // procese.
        if (c.obtenerTipo() == TipoCarta.ACCION) {
            return c;
        }

        // Si es numérica/bonus, la procesamos (con BUST/Second Chance)
        boolean sobrevivio = objetivo.intentarJalarCarta(c);

        // Si hubo BUST, devolvemos null, y SesionJuego terminará la secuencia.
        if (!sobrevivio) {
            return null;
        }

        // Si fue exitosa (numérica/bonus), devolvemos la carta para el reporte.
        return c;
    }

    public String transferirSecondChance(Jugador objetivo) {
        if (objetivo == null)
            return "Objetivo inválido.";

        // Si el objetivo NO está activo, NO puede recibirla
        if (objetivo.tieneBUST() || objetivo.sePlanto()) {
            return "Ese jugador ya no está activo y no puede recibir Second Chance.";
        }

        // Si ya tiene Second Chance, NO puede recibir otra
        if (objetivo.tieneSecondChance()) {
            return "Ese jugador ya tiene una Second Chance, no puede recibir otra.";
        }

        // Asignar correctamente
        objetivo.setTieneSecondChance(true);
        return objetivo.obtenerNombreUsuario() + " ha recibido una Second Chance.";
    }

}