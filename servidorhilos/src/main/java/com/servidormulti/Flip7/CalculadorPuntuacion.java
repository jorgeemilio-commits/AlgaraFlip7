package com.servidormulti.Flip7;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class CalculadorPuntuacion {
    public int calcularPuntuacion(List<Carta> mano) {
        int sumaBase = 0;
        int multiplicador = 1;
        int bonoPuntos = 0;

        Set<Integer> valoresUnicos = new HashSet<>();

        for (Carta c : mano) {
            if (c.obtenerTipo() == TipoCarta.NUMERICA) {
                sumaBase += c.obtenerValor();
                valoresUnicos.add(c.obtenerValor());
            } else if (c.obtenerTipo() == TipoCarta.BONUS) {
                if (c.toString().equals("x2")) {
                    multiplicador *= 2;
                } else if (c.toString().contains("+10")) {
                    bonoPuntos += 10;
                }
            }
        }

        int total = sumaBase * multiplicador;

        total += bonoPuntos;

        if (valoresUnicos.size() >= 7) {
            total += 15;
        }

        return total;
    }

    public boolean verificarFlip7(List<Carta> mano) {
        Set<Integer> valoresUnicos = new HashSet<>();

        for (Carta c : mano) {
            if (c.obtenerTipo() == TipoCarta.NUMERICA) {
                valoresUnicos.add(c.obtenerValor());
            }
        }
        return valoresUnicos.size() >= 7;
    }

}