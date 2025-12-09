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

    public String aplicarFlipThree(Jugador objetivo, Baraja baraja) {
        if (objetivo == null)
            return "Error: Objetivo nulo.";
        if (objetivo.sePlanto() || objetivo.tieneBUST())
            return "El objetivo ya no está jugando.";

        StringBuilder reporte = new StringBuilder();
        reporte.append(objetivo.obtenerNombreUsuario()).append(" sufre Flip Three:\n");

        boolean murio = false;

        for (int i = 0; i < 3; i++) {
            Carta c = baraja.jalarCarta();

            // Si se acaba la baraja a medio ataque
            if (c == null) {
                baraja.reiniciarBaraja();
                c = baraja.jalarCarta();
            }
            if (c == null)
                break;

            reporte.append("   -> Jaló: ").append(c.toString());

            // Intentamos dar la carta al objetivo
            boolean sobrevivio = objetivo.intentarJalarCarta(c);

            if (!sobrevivio) {
                reporte.append(" ¡BUST!");
                murio = true;
                break; // Se rompe el ciclo si hace BUST
            } else {
                reporte.append(" (OK)\n");
            }
        }

        if (!murio) {
            reporte.append("Sobrevivió al Flip Three.");
        }
        return reporte.toString();
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