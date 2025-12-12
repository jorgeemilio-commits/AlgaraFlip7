package com.servidormulti.Flip7;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class Baraja {
    private List<Carta> cartas;

    public Baraja() {
        this.cartas = new ArrayList<>();
        inicializarCartas();
        Collections.shuffle(this.cartas);
    }

    private void inicializarCartas() {
        for (int valor = 1; valor <= 12; valor++) {
            for (int cuenta = 0; cuenta < valor; cuenta++) {
                cartas.add(new Carta(valor, String.valueOf(valor), TipoCarta.NUMERICA));
            }
        }
        
        // Second Chance
        for (int i = 0; i < 3; i++) {
            cartas.add(new Carta(0, "Second Chance", TipoCarta.ACCION));
        }

        // Freeze (Congelar)
        for (int i = 0; i < 3; i++) {
            cartas.add(new Carta(0, "Freeze", TipoCarta.ACCION));
        }

        // Flip Three (Jalar 3)
        for (int i = 0; i < 3; i++) {
            cartas.add(new Carta(0, "Flip Three", TipoCarta.ACCION));
        }

        // Bonus (x2 y +10)
        cartas.add(new Carta(0, "x2", TipoCarta.BONUS));
        cartas.add(new Carta(0, "+10", TipoCarta.BONUS));

        // Barajear al final de la creación
        Collections.shuffle(this.cartas);
    }

    public Carta jalarCarta() {
        if (cartas.isEmpty()) {
            return null;
        }
        return cartas.remove(0);
    }

    // Método para el reinicio de la baraja (se llama al inicio de cada partida)
    public void reiniciarBaraja() {
        this.cartas.clear();
        inicializarCartas();
    }
}