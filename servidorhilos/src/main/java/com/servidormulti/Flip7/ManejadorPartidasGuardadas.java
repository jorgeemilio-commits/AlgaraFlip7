package com.servidormulti.Flip7;

import com.servidormulti.GrupoDB;
import com.servidormulti.UnCliente;
import java.util.List;
import java.util.Map;

public class ManejadorPartidasGuardadas {

    private final GrupoDB grupoDB;

    public ManejadorPartidasGuardadas() {
        this.grupoDB = new GrupoDB();
    }

    /**
     * Intenta cargar una partida desde la base de datos y restaurar el estado de los jugadores.
     * @return El índice del turno actual guardado, o -1 si falla.
     */
    public int cargarPartida(int idPartida, List<UnCliente> clientesEnSala, Map<String, Jugador> jugadores, Baraja baraja) {
        try {
            int turnoGuardado = grupoDB.obtenerTurnoGuardado(idPartida);
            List<GrupoDB.DatosJugadorGuardado> datos = grupoDB.cargarJugadoresDePartida(idPartida);
            
            // Reiniciamos la baraja para asegurar que está limpia antes de repartir
            baraja.reiniciarBaraja(); 

            boolean algunJugadorRestaurado = false;

            for (GrupoDB.DatosJugadorGuardado d : datos) {
                // Buscar al cliente conectado que corresponda al nombre guardado
                UnCliente clienteDueño = null;
                for (UnCliente c : clientesEnSala) {
                    if (c.getNombreUsuario().equalsIgnoreCase(d.nombre)) {
                        clienteDueño = c;
                        break;
                    }
                }
                
                // Si el jugador está en la sala, restauramos sus datos
                if (clienteDueño != null) {
                    Jugador j = jugadores.get(clienteDueño.getClienteID());
                    if (j != null) {
                        j.sumarPuntos(d.puntuacion); // Restaurar puntos
                        j.setTieneSecondChance(d.secondChance); // Restaurar vidas
                        
                        // Restaurar cartas en mano
                        if (d.cartas != null && !d.cartas.isEmpty()) {
                            String[] cartasArr = d.cartas.split(",");
                            for (String nombreCarta : cartasArr) {
                                Carta c = reconstruirCarta(nombreCarta);
                                if (c != null) {
                                    j.obtenerCartasEnMano().add(c);
                                }
                            }
                        }
                        algunJugadorRestaurado = true;
                    }
                }
            }

            if (algunJugadorRestaurado) {
                // Si cargamos con éxito, borramos el registro para evitar duplicados futuros
                grupoDB.eliminarPartidaGuardada(idPartida);
                return turnoGuardado;
            } else {
                return -1; // No se encontró a nadie válido
            }

        } catch (Exception e) {
            e.printStackTrace();
            return -1;
        }
    }

    // Helper para convertir el String de la BD de vuelta a Objeto Carta
    private Carta reconstruirCarta(String nombre) {
        try {
            if (nombre.equals("Second Chance")) return new Carta(0, "Second Chance", TipoCarta.ACCION);
            if (nombre.equals("Freeze")) return new Carta(0, "Freeze", TipoCarta.ACCION);
            if (nombre.equals("Flip Three")) return new Carta(0, "Flip Three", TipoCarta.ACCION);
            if (nombre.equals("x2")) return new Carta(0, "x2", TipoCarta.BONUS);
            if (nombre.equals("+10")) return new Carta(0, "+10", TipoCarta.BONUS);
            
            // Si es numérica
            int valor = Integer.parseInt(nombre);
            return new Carta(valor, String.valueOf(valor), TipoCarta.NUMERICA);
        } catch (Exception e) {
            return null;
        }
    }
}