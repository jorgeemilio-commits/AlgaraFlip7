package com.servidormulti.Flip7;

public class Carta {
    private final int valor;
    private final String nombre;
    private final TipoCarta tipo;

    public Carta(int valor, String nombre, TipoCarta tipo) {
        this.valor = valor;
        this.nombre = nombre;
        this.tipo = tipo;
    }

    public int obtenerValor() {
        return valor;
    }

    public TipoCarta obtenerTipo() {
        return tipo;
    }

    @Override
    public String toString() {
        return nombre;
    }
}
