package com.servidormulti.Flip7;

import com.servidormulti.UnCliente;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

public class SesionJuego {

    private final List<UnCliente> clientesEnSala;
    private final Map<String, Jugador> jugadores; // Mapa ID_Cliente -> JugadorLogico
    private final Baraja baraja;
    private final CalculadorPuntuacion calculadora;
    private final ManejadorAcciones manejadorAcciones;
    private boolean juegoIniciado = false;
    private int indiceTurnoActual = 0;
    private boolean esperandoObjetivo = false;
    private Carta accionPendiente = null;

    public SesionJuego(List<UnCliente> clientes) {
        this.clientesEnSala = clientes;
        this.jugadores = new HashMap<>();
        this.baraja = new Baraja();
        this.calculadora = new CalculadorPuntuacion();

        // Inicializamos el manejador de acciones
        this.manejadorAcciones = new ManejadorAcciones();
    }

    // Lógica separada para iniciar partida vs siguiente ronda
    public void iniciarPartida() {
        if (juegoIniciado) return;

        // Reinicio TOTAL (Solo cuando escriben /listo al principio o tras ganar)
        jugadores.clear();
        for (UnCliente c : clientesEnSala) {
            jugadores.put(c.getClienteID(), new Jugador(c.getNombreUsuario()));
        }
        
        configurarYArrancarRonda("--- ¡LA PARTIDA DE FLIP7 HA COMENZADO! ---");
    }

    private void iniciarSiguienteRonda() {
        if (clientesEnSala.isEmpty()) {
            juegoIniciado = false;
            return;
        }

        // Reinicio PARCIAL (Mantiene puntos acumulados, solo limpia mano y estados)
        for (Jugador j : jugadores.values()) {
            j.reiniciarParaRondaNueva();
        }

        configurarYArrancarRonda("--- INICIANDO SIGUIENTE RONDA ---");
    }

    private void configurarYArrancarRonda(String mensajeInicio) {
        baraja.reiniciarBaraja();
        if (!clientesEnSala.isEmpty()) {
            indiceTurnoActual = new Random().nextInt(clientesEnSala.size());
        }
        
        juegoIniciado = true;
        esperandoObjetivo = false; 

        broadcastMensaje(mensajeInicio);
        anunciarTurno();
    }

   public void procesarMensajeJuego(UnCliente remitente, String mensaje) {
        if (!juegoIniciado) return;

        // [NUEVO] - Habilitar Chat Global
        // Si no empieza con "/", es chat y se envía a todos.
        if (!mensaje.trim().startsWith("/")) {
            broadcastMensaje("<" + remitente.getNombreUsuario() + ">: " + mensaje);
            return; 
        }

        String[] partes = mensaje.trim().split("\\s+");
        String comando = partes[0].toLowerCase();

       // Permitir ver puntuación siempre
        if (comando.equals("/puntuacion")) {
            enviarMensajePrivado(remitente, obtenerReportePuntuacion());
            return;
        }

        UnCliente clienteActual = clientesEnSala.get(indiceTurnoActual);
        if (!remitente.getClienteID().equals(clienteActual.getClienteID())) {
            enviarMensajePrivado(remitente, "No es tu turno. Espera a " + clienteActual.getNombreUsuario());
            return;
        }

        Jugador jugadorActual = jugadores.get(remitente.getClienteID());

        if (esperandoObjetivo) {
            if (comando.equals("/usar")) {
                if (partes.length < 2) {
                    enviarMensajePrivado(remitente, "Debes especificar un nombre. Ej: /usar Juan");
                    return;
                }
                ejecutarAccionPendiente(remitente, partes[1]);
            } else {
                enviarMensajePrivado(remitente, "¡Tienes una carta de ACCIÓN pendiente! Usa: /usar [Nombre]");
            }
            return;
        }

        switch (comando) {
            case "/jalar":
                accionJalar(remitente, jugadorActual);
                break;
            case "/parar":
                accionParar(remitente, jugadorActual);
                break;
            default:
                enviarMensajePrivado(remitente, "Comando no válido. Usa /jalar, /parar, /puntuacion o escribe normal para chatear.");
        }
    }

    private void accionJalar(UnCliente cliente, Jugador jugador) {
        Carta carta = baraja.jalarCarta();

        if (carta == null) {
            broadcastMensaje("¡Se acabó la baraja! Barajeando descarte...");
            baraja.reiniciarBaraja();
            carta = baraja.jalarCarta();
        }

        if (carta == null) {
            broadcastMensaje("Error: El mazo sigue vacío después de reiniciar. Finalizando ronda...");
            finalizarRonda();
            return;
        }

        broadcastMensaje(cliente.getNombreUsuario() + " jaló: " + carta);

        // DETECCIÓN DE CARTAS DE ACCIÓN
        if (carta.obtenerTipo() == TipoCarta.ACCION) {
            procesarCartaAccion(cliente, jugador, carta);
            return; // Salimos para esperar input del usuario o resolver efecto
        }

        // Lógica Normal (Numéricas y Bonus)
        boolean sobrevivio = jugador.intentarJalarCarta(carta);

        if (!sobrevivio) {
            broadcastMensaje("¡BUST! " + cliente.getNombreUsuario() + " ha perdido la ronda.");
            siguienteTurno();
        } else {
            if (calculadora.verificarFlip7(jugador.obtenerCartasEnMano())) {
                broadcastMensaje("\n FLIP 7 " + cliente.getNombreUsuario()
                        + " ha conseguido 7 cartas únicas. La ronda termina inmediatamente.");
                finalizarRonda();
                return;
            }

            enviarMensajePrivado(cliente, "Tu mano actual: " + jugador.obtenerCartasEnMano());
            siguienteTurno(); 
        }
    }

    private void procesarCartaAccion(UnCliente cliente, Jugador jugador, Carta carta) {
        String nombreCarta = carta.toString();
        boolean requiereObjetivo = false;
        StringBuilder listaObjetivos = new StringBuilder();
        int contadorValidos = 0;

        // Construir listas filtradas según la carta
        if (nombreCarta.equals("Second Chance") && jugador.tieneSecondChance()) {
            requiereObjetivo = true;
            listaObjetivos.append("--- JUGADORES DISPONIBLES ---\n");
            for (UnCliente c : clientesEnSala) {
                if (!c.getClienteID().equals(cliente.getClienteID())) {
                    listaObjetivos.append(" - ").append(c.getNombreUsuario()).append("\n");
                    contadorValidos++;
                }
            }
        } else if (nombreCarta.equals("Freeze") || nombreCarta.equals("Flip Three")) {
            requiereObjetivo = true;
            listaObjetivos.append("--- VÍCTIMAS DISPONIBLES ---\n");
            for (UnCliente c : clientesEnSala) {
                Jugador j = jugadores.get(c.getClienteID());
                // Filtro: No atacar a plantados ni BUST
                if (!j.sePlanto() && !j.tieneBUST()) {
                    listaObjetivos.append(" - ").append(c.getNombreUsuario());
                    if (c.getClienteID().equals(cliente.getClienteID())) listaObjetivos.append(" [TÚ]");
                    listaObjetivos.append("\n");
                    contadorValidos++;
                }
            }
        } else if (nombreCarta.equals("Second Chance") && !jugador.tieneSecondChance()) {
            // Auto-asignar si no tiene vida extra
            jugador.setTieneSecondChance(true);
            broadcastMensaje(cliente.getNombreUsuario() + " obtuvo Second Chance.");
            enviarMensajePrivado(cliente, "¿Quieres /jalar otra o /parar?");
            return;
        }

        // Si requiere objetivo, validar si existen candidatos
        if (requiereObjetivo) {
            if (contadorValidos > 0) {
                this.accionPendiente = carta;
                this.esperandoObjetivo = true;
                enviarMensajePrivado(cliente, "¡Sacaste " + nombreCarta + "!");
                enviarMensajePrivado(cliente, listaObjetivos.toString());
                enviarMensajePrivado(cliente, "Usa: /usar [Nombre]");
            } else {
                // Descarte automático si nadie es válido
                broadcastMensaje(cliente.getNombreUsuario() + " sacó " + nombreCarta + " pero se descarta (sin objetivos).");
                enviarMensajePrivado(cliente, "¿Quieres /jalar otra o /parar?");
            }
        }
    }

    private void ejecutarAccionPendiente(UnCliente atacante, String nombreObjetivo) {
        // Buscar al cliente objetivo por nombre
        UnCliente clienteObj = null;
        for (UnCliente c : clientesEnSala) {
            if (c.getNombreUsuario().equalsIgnoreCase(nombreObjetivo)) {
                clienteObj = c;
                break;
            }
        }

        if (clienteObj == null) {
            enviarMensajePrivado(atacante,
                    "Jugador '" + nombreObjetivo + "' no encontrado en la sala. Intenta de nuevo.");
            return;
        }

        // Obtener el Jugador lógico
        Jugador objetivo = jugadores.get(clienteObj.getClienteID());
        String resultado = "";

        // Ejecutar la acción correspondiente
        String nombreAccion = accionPendiente.toString();

        if (nombreAccion.equals("Second Chance")) {
            resultado = manejadorAcciones.transferirSecondChance(objetivo);

        } else if (nombreAccion.equals("Freeze")) {
            resultado = manejadorAcciones.aplicarFreeze(objetivo);

        } else if (nombreAccion.equals("Flip Three")) {
            resultado = manejadorAcciones.aplicarFlipThree(objetivo, baraja);
        }

        // Anunciar resultado
        broadcastMensaje("ACCIÓN " + nombreAccion + ": " + resultado);

        // Limpiar estado de acción
        this.esperandoObjetivo = false;
        this.accionPendiente = null;

        // CORRECCIÓN CLAVE: El turno del atacante debe terminar después de usar la
        // acción.
        siguienteTurno();
    }

    private void accionParar(UnCliente cliente, Jugador jugador) {
        jugador.plantarse();
        int puntos = calculadora.calcularPuntuacion(jugador.obtenerCartasEnMano());
        broadcastMensaje(cliente.getNombreUsuario() + " se ha PLANTADO con " + puntos + " puntos provisionales.");
        siguienteTurno();
    }

    private void siguienteTurno() {
        if (verificarFinDeRonda()) {
            finalizarRonda();
            return;
        }

        int intentos = 0;
        do {
            indiceTurnoActual = (indiceTurnoActual + 1) % clientesEnSala.size();
            UnCliente proximoCliente = clientesEnSala.get(indiceTurnoActual);
            Jugador proximoJugador = jugadores.get(proximoCliente.getClienteID());

            // Solo pasa el turno si NO tiene BUST y NO se plantó
            if (!proximoJugador.tieneBUST() && !proximoJugador.sePlanto()) {
                anunciarTurno();
                return;
            }
            intentos++;
        } while (intentos < clientesEnSala.size());

        finalizarRonda();
    }

    private boolean verificarFinDeRonda() {
        for (Jugador j : jugadores.values()) {
            if (!j.tieneBUST() && !j.sePlanto()) {
                return false;
            }
        }
        return true;
    }

    private void finalizarRonda() {
        broadcastMensaje("\n--- FIN DE LA RONDA ---");
        broadcastMensaje("Resultados:");

        for (UnCliente c : clientesEnSala) {
            Jugador j = jugadores.get(c.getClienteID());
            int puntos = j.tieneBUST() ? 0 : calculadora.calcularPuntuacion(j.obtenerCartasEnMano());
            broadcastMensaje(
                    " -> " + c.getNombreUsuario() + ": " + puntos + " puntos. (" + j.obtenerCartasEnMano() + ")");
        }
        juegoIniciado = false;
        broadcastMensaje("Escriban /listo para iniciar otra vez.");
    }

    private void anunciarTurno() {
        UnCliente actual = clientesEnSala.get(indiceTurnoActual);
        broadcastMensaje("\n>>> Turno de: " + actual.getNombreUsuario() + " <<<");
        enviarMensajePrivado(actual, "Es tu turno. Escribe /jalar o /parar");
    }

    private void broadcastMensaje(String msg) {
        for (UnCliente c : clientesEnSala) {
            try {
                c.getSalida().writeUTF(msg);
            } catch (IOException e) {
                /* Ignorar desconexión */ }
        }
    }

    private void enviarMensajePrivado(UnCliente c, String msg) {
        try {
            c.getSalida().writeUTF(msg);
        } catch (IOException e) {
            /* Ignorar */ }
    }

    public boolean estaJuegoIniciado() {
        return juegoIniciado;
    }

    public String obtenerReportePuntuacion() {
        StringBuilder reporte = new StringBuilder("\n--- PUNTAJES ACTUALES ---\n");

        for (Jugador j : jugadores.values()) {
            reporte.append(j.obtenerNombreUsuario())
                    .append(": ")
                    .append(j.obtenerPuntuacionTotal())
                    .append(" pts\n");
        }
        reporte.append("-------------------------\n");
        return reporte.toString();
    }
}