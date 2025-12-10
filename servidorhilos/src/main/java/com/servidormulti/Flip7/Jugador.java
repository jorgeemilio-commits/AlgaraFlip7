package com.servidormulti.Flip7;

import java.util.ArrayList;
import java.util.List;

public class Jugador {
    private final String nombreUsuario;
    private List<Carta> cartasEnMano;
    private boolean tieneBUST;
    private boolean sePlanto;
    private int puntuacionTotal;

    private boolean tieneSecondChance;
    private boolean estaCongelado;

    public Jugador(String nombreUsuario) {
        this.nombreUsuario = nombreUsuario;
        this.cartasEnMano = new ArrayList<>();
        this.tieneBUST = false;
        this.sePlanto = false;
        this.tieneSecondChance = false;
        this.estaCongelado = false; // Inicializamos
        this.puntuacionTotal = 0;
    }

    // Comando /parar
    public void plantarse() {
        if (!tieneBUST) {
            this.sePlanto = true;
        }
    }

    // Método para Congelar
    public void congelar() {
        this.estaCongelado = true;
        plantarse(); // Al congelarse, fuerza el STAY
    }

    public boolean intentarJalarCarta(Carta nuevaCarta) {
        if (tieneBUST || sePlanto) return false;

        // Si es Bonus/Accion se agrega directo...
        if (nuevaCarta.obtenerTipo() != TipoCarta.NUMERICA) {
            this.cartasEnMano.add(nuevaCarta);
            return true;
        }

        // Lógica de BUST
        if (verificarSiCausaBUST(nuevaCarta)) {
            if (this.tieneSecondChance) {
                this.tieneSecondChance = false; // Se gasta la vida
              
                return true; 
            } else {
                this.cartasEnMano.clear(); // Se borra todo
                this.tieneBUST = true;
                this.sePlanto = true;
                return false;
            }
        } else {
            this.cartasEnMano.add(nuevaCarta);
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
        // Reiniciamos estados especiales también
        this.tieneSecondChance = false;
        this.estaCongelado = false;
    }

    public void sumarPuntos(int puntos) {
        this.puntuacionTotal += puntos;
    }

    public int obtenerPuntuacionTotal() {
        return puntuacionTotal;
    }

    public String obtenerNombreUsuario() {
        return nombreUsuario;
    }

    public boolean tieneBUST() {
        return tieneBUST;
    }

    public boolean sePlanto() {
        return sePlanto;
    }

    public List<Carta> obtenerCartasEnMano() {
        return cartasEnMano;
    }

    // Getters y Setters
    public void setTieneSecondChance(boolean valor) {
        this.tieneSecondChance = valor;
    }

    public boolean tieneSecondChance() {
        return tieneSecondChance;
    }

    public boolean estaCongelado() {
        return estaCongelado;
    }
}