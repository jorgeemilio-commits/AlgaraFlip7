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

    public void iniciarPartida() {
        if (juegoIniciado)
            return;

        jugadores.clear();
        for (UnCliente c : clientesEnSala) {
            jugadores.put(c.getClienteID(), new Jugador(c.getNombreUsuario()));
        }
        baraja.reiniciarBaraja();
        indiceTurnoActual = new Random().nextInt(clientesEnSala.size());

        juegoIniciado = true;
        esperandoObjetivo = false; // Resetear estado de acciones

        broadcastMensaje("--- ¡LA PARTIDA DE FLIP7 HA COMENZADO! ---");
        anunciarTurno();
    }

    public void procesarMensajeJuego(UnCliente remitente, String mensaje) {
        if (!juegoIniciado)
            return;

        // HABILITAR CHAT
        // Si NO empieza con "/", es un mensaje de chat para todos.
        if (!mensaje.trim().startsWith("/")) {
            broadcastMensaje("<" + remitente.getNombreUsuario() + ">: " + mensaje);
            return; // Terminamos aquí, no procesamos como comando.
        }

        UnCliente clienteActual = clientesEnSala.get(indiceTurnoActual);
        if (!remitente.getClienteID().equals(clienteActual.getClienteID())) {
            enviarMensajePrivado(remitente, "No es tu turno. Puedes chatear, pero espera a "
                    + clienteActual.getNombreUsuario() + " para jugar.");
            return;
        }

        Jugador jugadorActual = jugadores.get(remitente.getClienteID());

        String[] partes = mensaje.trim().split("\\s+");
        String comando = partes[0].toLowerCase();

        if (esperandoObjetivo) {
            if (comando.equals("/usar")) {
                if (partes.length < 2) {
                    enviarMensajePrivado(remitente, "Debes especificar un nombre. Ej: /usar Juan");
                    return;
                }
                String nombreObjetivo = partes[1];
                ejecutarAccionPendiente(remitente, nombreObjetivo);
            } else {
                enviarMensajePrivado(remitente, "¡Tienes una carta de ACCIÓN pendiente! Usa: /usar [NombreDeLaLista]");
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
                enviarMensajePrivado(remitente, "Comando no válido. Usa /jalar, /parar o escribe normal para chatear.");
        }
    }

    private void accionJalar(UnCliente cliente, Jugador jugador) {
        Carta carta = baraja.jalarCarta();

        if (carta == null) {
            broadcastMensaje("¡Se acabó la baraja! Barajeando descarte...");
            baraja.reiniciarBaraja();
            carta = baraja.jalarCarta();
        }

        broadcastMensaje(cliente.getNombreUsuario() + " jaló: " + carta);

        // DETECCIÓN DE CARTAS DE ACCIÓN
        if (carta.obtenerTipo() == TipoCarta.ACCION) {
            procesarCartaAccion(cliente, jugador, carta);
            return; // Salimos para esperar input del usuario o resolver efecto
        }

        if (carta == null) {
            broadcastMensaje("Error: El mazo sigue vacío después de reiniciar. Finalizando ronda...");
            finalizarRonda();
            return;
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
            enviarMensajePrivado(cliente, "¿Quieres /jalar otra o /parar?");
        }
    }

    private void procesarCartaAccion(UnCliente cliente, Jugador jugador, Carta carta) {
        String nombreCarta = carta.toString();
        boolean requiereObjetivo = false;

        // Lista para guardar nombres de posibles víctimas
        StringBuilder listaObjetivos = new StringBuilder();
        int contadorValidos = 0;

        // Lógica específica para filtrar quién es válido
        if (nombreCarta.equals("Second Chance") && jugador.tieneSecondChance()) {
            requiereObjetivo = true;
            // Para regalar vida, buscamos a cualquiera que NO sea yo
            listaObjetivos.append("--- JUGADORES DISPONIBLES PARA REGALAR VIDA ---\n");
            for (UnCliente c : clientesEnSala) {
                if (!c.getClienteID().equals(cliente.getClienteID())) {
                    listaObjetivos.append(" - ").append(c.getNombreUsuario()).append("\n");
                    contadorValidos++;
                }
            }
        } else if (nombreCarta.equals("Freeze") || nombreCarta.equals("Flip Three")) {
            requiereObjetivo = true;
            // Para atacar, buscamos a quienes NO se han plantado ni perdido
            listaObjetivos.append("--- VÍCTIMAS DISPONIBLES ---\n");
            for (UnCliente c : clientesEnSala) {
                Jugador j = jugadores.get(c.getClienteID());
                // No me ataco a mí mismo, ni a los que ya terminaron (plantados/bust)
                if (!c.getClienteID().equals(cliente.getClienteID()) && !j.sePlanto() && !j.tieneBUST()) {
                    listaObjetivos.append(" - ").append(c.getNombreUsuario()).append("\n");
                    contadorValidos++;
                }
            }
        } else if (nombreCarta.equals("Second Chance") && !jugador.tieneSecondChance()) {
            // Caso simple: Se la queda él mismo
            jugador.setTieneSecondChance(true);
            broadcastMensaje(cliente.getNombreUsuario() + " se queda con una Second Chance (Vida Extra).");
            enviarMensajePrivado(cliente, "¿Quieres /jalar otra o /parar?");
            return;
        }

        if (requiereObjetivo) {
            if (contadorValidos > 0) {
                // Si hay víctimas, activamos el modo espera y mostramos la lista
                this.accionPendiente = carta;
                this.esperandoObjetivo = true;
                enviarMensajePrivado(cliente, "¡Sacaste " + nombreCarta + "!");
                enviarMensajePrivado(cliente, listaObjetivos.toString());
                enviarMensajePrivado(cliente, "Escribe: /usar [Nombre] para aplicarla.");
            } else {
                // Si NO hay nadie a quien atacar/regalar, la carta se descarta
                broadcastMensaje(cliente.getNombreUsuario() + " sacó " + nombreCarta
                        + ", pero no hay objetivos válidos. La carta se descarta.");
                enviarMensajePrivado(cliente, "No hay nadie a quien aplicarle la carta. Turno continúa.");
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

        // El turno continúa para el jugador actual después de usar la acción
        enviarMensajePrivado(atacante, "¿Quieres /jalar otra o /parar?");
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
}