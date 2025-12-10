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

    private int flipThreeCartasRestantes = 0;
    private Jugador flipThreeObjetivo = null;
    private UnCliente flipThreeAtacante = null;

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
        if (juegoIniciado)
            return;

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
        if (!juegoIniciado)
            return;

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
                enviarMensajePrivado(remitente,
                        "Comando no válido. Usa /jalar, /parar, /puntuacion o escribe normal para chatear.");
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

        if (nombreCarta.equals("Second Chance") && jugador.tieneSecondChance()) {

            requiereObjetivo = true;
            listaObjetivos.append("--- JUGADORES DISPONIBLES ---\n");

            for (UnCliente c : clientesEnSala) {

                if (c.getClienteID().equals(cliente.getClienteID()))
                    continue; // no tú mismo

                Jugador j = jugadores.get(c.getClienteID());

                // Solo jugadores activos y sin Second Chance
                if (!j.tieneBUST() && !j.sePlanto() && !j.tieneSecondChance()) {
                    listaObjetivos.append(" - ").append(c.getNombreUsuario()).append("\n");
                    contadorValidos++;
                }
            }

            // Si no hay candidatos → descartar la carta
            if (contadorValidos == 0) {
                broadcastMensaje(cliente.getNombreUsuario()
                        + " sacó Second Chance pero no hay jugadores activos disponibles. Se DESCARTA.");
                enviarMensajePrivado(cliente, "¿Quieres /jalar otra o /parar?");
                return;
            }
        }

        else if (nombreCarta.equals("Freeze") || nombreCarta.equals("Flip Three")) {

            requiereObjetivo = true;
            listaObjetivos.append("--- VÍCTIMAS DISPONIBLES ---\n");

            for (UnCliente c : clientesEnSala) {
                Jugador j = jugadores.get(c.getClienteID());

                if (!j.sePlanto() && !j.tieneBUST()) {
                    listaObjetivos.append(" - ").append(c.getNombreUsuario());
                    if (c.getClienteID().equals(cliente.getClienteID()))
                        listaObjetivos.append(" [TÚ]");
                    listaObjetivos.append("\n");
                    contadorValidos++;
                }
            }

            // Si nadie es atacable
            if (contadorValidos == 0) {
                broadcastMensaje(cliente.getNombreUsuario() + " sacó " + nombreCarta
                        + " pero no hay jugadores activos disponibles.");
                enviarMensajePrivado(cliente, "¿Quieres /jalar otra o /parar?");
                return;
            }
        }

        else if (nombreCarta.equals("Second Chance") && !jugador.tieneSecondChance()) {

            jugador.setTieneSecondChance(true);
            broadcastMensaje(cliente.getNombreUsuario() + " obtuvo una Second Chance.");
            enviarMensajePrivado(cliente, "¿Quieres /jalar otra o /parar?");
            return;
        }

        if (requiereObjetivo) {

            this.accionPendiente = carta;
            this.esperandoObjetivo = true;

            enviarMensajePrivado(cliente, "¡Sacaste " + nombreCarta + "!");
            enviarMensajePrivado(cliente, listaObjetivos.toString());
            enviarMensajePrivado(cliente, "Usa: /usar [Nombre]");
        }
    }

    /*
     * private void ejecutarAccionPendiente(UnCliente atacante, String
     * nombreObjetivo) {
     * // Buscar al cliente objetivo por nombre
     * UnCliente clienteObj = null;
     * for (UnCliente c : clientesEnSala) {
     * if (c.getNombreUsuario().equalsIgnoreCase(nombreObjetivo)) {
     * clienteObj = c;
     * break;
     * }
     * }
     * 
     * if (clienteObj == null) {
     * enviarMensajePrivado(atacante,
     * "Jugador '" + nombreObjetivo +
     * "' no encontrado en la sala. Intenta de nuevo.");
     * return;
     * }
     * 
     * // Obtener el Jugador lógico
     * Jugador objetivo = jugadores.get(clienteObj.getClienteID());
     * String resultado = "";
     * 
     * // Ejecutar la acción correspondiente
     * String nombreAccion = accionPendiente.toString();
     * 
     * if (nombreAccion.equals("Second Chance")) {
     * resultado = manejadorAcciones.transferirSecondChance(objetivo);
     * 
     * } else if (nombreAccion.equals("Freeze")) {
     * resultado = manejadorAcciones.aplicarFreeze(objetivo);
     * 
     * } else if (nombreAccion.equals("Flip Three")) {
     * // Llamada al método corregido (que maneja el reporte y la interrupción)
     * resultado = manejadorAcciones.aplicarFlipThree(objetivo, baraja);
     * }
     * 
     * // Anunciar resultado
     * broadcastMensaje("ACCIÓN " + nombreAccion + ": " + resultado);
     * 
     * // Limpiar estado de acción
     * this.esperandoObjetivo = false;
     * this.accionPendiente = null;
     * 
     * // CORRECCIÓN DE FLUJO: El turno del atacante debe terminar después de usar
     * la
     * // acción.
     * siguienteTurno();
     * }
     */

    //cambio de metodo ejecutarAccionPendiente para corregir flip three
    private void ejecutarAccionPendiente(UnCliente atacante, String nombreObjetivo) {
        // Lógica de búsqueda de clienteObj y objetivo (la dejamos como la tenías)
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

        Jugador objetivo = jugadores.get(clienteObj.getClienteID());

        String nombreAccion = accionPendiente.toString();

        if (nombreAccion.equals("Flip Three")) {
            // INICIO: Si es la primera vez que se ejecuta el Flip Three, iniciamos el
            // estado.
            this.flipThreeCartasRestantes = 3;
            this.flipThreeObjetivo = objetivo;
            this.flipThreeAtacante = atacante;

            // Limpiamos el estado pendiente para evitar confusión.
            this.esperandoObjetivo = false;
            this.accionPendiente = null;

            // Pasamos el control al motor del Flip Three.
            iniciarOReanudarFlipThree(atacante, objetivo);
            return;

        } else if (nombreAccion.equals("Second Chance") || nombreAccion.equals("Freeze")) {

            // Ejecución de acciones simples
            if (nombreAccion.equals("Second Chance")) {
                manejadorAcciones.transferirSecondChance(objetivo);
            } else if (nombreAccion.equals("Freeze")) {
                manejadorAcciones.aplicarFreeze(objetivo);
            }

            // -----------------------------------------------------------
            // LÓGICA DE REANUDACIÓN DE FLIP THREE
            // -----------------------------------------------------------
            if (this.flipThreeCartasRestantes > 0 && this.flipThreeObjetivo != null) {

                // Si la acción que se acaba de resolver fue jalada *durante* un Flip Three,
                // reanudamos la secuencia llamando al motor.
                UnCliente reanudandoAtacante = this.flipThreeAtacante;
                Jugador reanudandoObjetivo = this.flipThreeObjetivo;

                // Limpiamos la acción actual antes de reanudar
                this.esperandoObjetivo = false;
                this.accionPendiente = null;

                // Reanudamos el ataque Flip Three desde el punto de interrupción
                iniciarOReanudarFlipThree(reanudandoAtacante, reanudandoObjetivo);
                return;
            }
            // Anunciar resultado
            broadcastMensaje(
                    "ACCIÓN " + nombreAccion + " ejecutada. " + objetivo.obtenerNombreUsuario() + " afectado.");

            // Limpiar estado de acción
            this.esperandoObjetivo = false;
            this.accionPendiente = null;

            siguienteTurno();
        }
    }

    //nuevo metodo flip three corregido
    private void iniciarOReanudarFlipThree(UnCliente atacante, Jugador objetivo) {
        // Si la acción se acaba de resolver (ej: Freeze), el atacante es el que tiene
        // el turno.
        String nombreAccion = (accionPendiente != null) ? accionPendiente.toString() : "Flip Three";

        broadcastMensaje("ACCIÓN " + nombreAccion + " ejecutada.");

        // Limpiar acción pendiente después de su uso (si la hubo)
        this.esperandoObjetivo = false;
        this.accionPendiente = null;

        // Bucle para procesar las cartas restantes (se detiene en 0, BUST, o ACCIÓN)
        while (this.flipThreeCartasRestantes > 0 && !objetivo.tieneBUST()) {

            broadcastMensaje(objetivo.obtenerNombreUsuario() + " está en Flip Three. Cartas restantes: "
                    + this.flipThreeCartasRestantes);

            // 1. Jalar una carta
            Carta cartaJalada = manejadorAcciones.jalarUnaCartaFlipThree(objetivo, baraja);

            this.flipThreeCartasRestantes--; // Decrementamos el contador inmediatamente

            if (objetivo.tieneBUST()) {
                broadcastMensaje(objetivo.obtenerNombreUsuario() + " sufrió BUST durante Flip Three!");
                this.flipThreeCartasRestantes = 0;
                break;
            }

            if (cartaJalada == null) {
                // Error de mazo o BUST no capturado. Se asume que el BUST se capturó arriba.
                continue;
            }

            if (cartaJalada.obtenerTipo() == TipoCarta.ACCION) {
                broadcastMensaje("Flip Three PAUSADO. " + atacante.getNombreUsuario() + " debe usar la acción "
                        + cartaJalada.toString() + ".");

                // Pausar el turno y pedir la decisión para la NUEVA acción
                this.accionPendiente = cartaJalada;
                this.esperandoObjetivo = true;

                // Guardamos el estado para que ejecutarAccionPendiente sepa reanudar
                this.flipThreeAtacante = atacante;
                this.flipThreeObjetivo = objetivo;

                // Toca al atacante usar la carta de acción recién jalada.
                procesarCartaAccion(atacante, jugadores.get(atacante.getClienteID()), cartaJalada);

                // Devolvemos el control al cliente (pausa)
                return;
            }

            // Si es Numérica/Bonus, el Flip Three continúa
            broadcastMensaje(objetivo.obtenerNombreUsuario() + " jaló " + cartaJalada.toString()
                    + ". Cartas restantes: " + this.flipThreeCartasRestantes);
        } // Fin del bucle WHILE

        // Si salimos del bucle, el ataque Flip Three ha terminado.
        if (this.flipThreeCartasRestantes == 0) {
            broadcastMensaje("Flip Three completado.");
            // Limpiar variables de estado global después de terminar la secuencia
            this.flipThreeObjetivo = null;
            this.flipThreeAtacante = null;
            siguienteTurno(); // Pasar el turno después de todo el proceso
        }
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

        Jugador ganadorDelJuego = null;
        int maxPuntuacionGlobal = -1;
        StringBuilder resumen = new StringBuilder("Resultados:\n");

        for (UnCliente c : clientesEnSala) {
            Jugador j = jugadores.get(c.getClienteID());
            int puntosRonda = j.tieneBUST() ? 0 : calculadora.calcularPuntuacion(j.obtenerCartasEnMano());

            // Acumular puntos
            j.sumarPuntos(puntosRonda);
            int total = j.obtenerPuntuacionTotal();

            resumen.append(" -> ").append(c.getNombreUsuario())
                    .append(": +").append(puntosRonda)
                    .append(" (Total: ").append(total).append(")\n");

            // Checar si alguien ganó la partida completa (>= 200)
            if (total >= 200) {
                if (total > maxPuntuacionGlobal) {
                    maxPuntuacionGlobal = total;
                    ganadorDelJuego = j;
                }
            }
        }
        broadcastMensaje(resumen.toString());

        juegoIniciado = false;

        if (ganadorDelJuego != null) {
            // Fin definitivo del juego
            broadcastMensaje("\n🏆 ¡JUEGO TERMINADO! 🏆");
            broadcastMensaje("El ganador es: " + ganadorDelJuego.obtenerNombreUsuario().toUpperCase());
            broadcastMensaje("Puntuación Final: " + maxPuntuacionGlobal);
            broadcastMensaje("------------------------------------------");
            broadcastMensaje("Escriban /listo para iniciar una partida nueva.");
        } else {
            // [NUEVO] Continuar siguiente ronda automáticamente
            broadcastMensaje("Nadie ha llegado a 200 puntos. La siguiente ronda comienza en 15 segundos...");

            new Thread(() -> {
                try {
                    Thread.sleep(15000); // 15 segundos
                    broadcastMensaje("¡Tiempo fuera! Preparando cartas...");
                    iniciarSiguienteRonda();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }).start();
        }
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