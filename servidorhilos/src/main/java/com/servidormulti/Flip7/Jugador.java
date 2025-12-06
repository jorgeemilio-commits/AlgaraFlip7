package com.servidormulti.Flip7;

import java.util.ArrayList;
import java.util.List;

public class Jugador {
    private final String nombreUsuario;
    private List<Carta> cartasEnMano;
    private boolean tieneBUST;
    private boolean sePlanto;
    private boolean tieneSecondChance; 

    public Jugador(String nombreUsuario) {
        this.nombreUsuario = nombreUsuario;
        this.cartasEnMano = new ArrayList<>();
        this.tieneBUST = false;
        this.sePlanto = false;
        this.tieneSecondChance = false; 
    }

    // Comando /parar 
    public void plantarse() {
        if (!tieneBUST) {
            this.sePlanto = true;
        }
    }

    public boolean intentarJalarCarta(Carta nuevaCarta) {
        if (tieneBUST || sePlanto) {
            return false;
        }

        // Lógica de BUST 
        if (nuevaCarta.obtenerTipo() == TipoCarta.NUMERICA && verificarSiCausaBUST(nuevaCarta)) {
            if (this.tieneSecondChance) {
                this.tieneSecondChance = false; 
                this.cartasEnMano.add(nuevaCarta); 
                return true; 
            } else {
                // BUST definitivo. Anular puntos y finalizar turno 
                this.cartasEnMano.clear(); 
                this.tieneBUST = true;
                this.sePlanto = true; 
                return false;
            }
        } else {
            if (nuevaCarta.obtenerTipo() == TipoCarta.NUMERICA) {
                 this.cartasEnMano.add(nuevaCarta);
            }
            return true;
        }
    }

    // Método que implementa detectar si una carta numérica repite valor 
    private boolean verificarSiCausaBUST(Carta nuevaCarta) {
        for (Carta carta : cartasEnMano) {
            if (carta.obtenerTipo() == TipoCarta.NUMERICA && carta.obtenerValor() == nuevaCarta.obtenerValor()) {
                return true; 
            }
        }
        return false;
    }
    
    // Reiniciar estado para nueva ronda
    public void reiniciarParaRondaNueva() {
        this.cartasEnMano.clear();
        this.tieneBUST = false;
        this.sePlanto = false;
    }

    public String obtenerNombreUsuario() { return nombreUsuario; }
    public boolean tieneBUST() { return tieneBUST; }
    public boolean sePlanto() { return sePlanto; }
    public List<Carta> obtenerCartasEnMano() { return cartasEnMano; }
    public void setTieneSecondChance(boolean valor) { this.tieneSecondChance = valor; }
}