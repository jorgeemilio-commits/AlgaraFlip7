package com.servidormulti.Flip7;

import com.servidormulti.UnCliente;
import java.io.IOException;
import java.util.ArrayList;
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
    // Lista para guardar las acciones (Freeze, Flip3) que salen DURANTE un Flip Three activo
private List<Carta> accionesAcumuladasFlipThree = new ArrayList<>();

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

        baraja.reiniciarBaraja();

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

       
        if (!mensaje.trim().startsWith("/")) {
            broadcastMensaje("<" + remitente.getNombreUsuario() + ">: " + mensaje);
            return; 
        }

        String[] partes = mensaje.trim().split("\\s+");
        String comando = partes[0].toLowerCase();

        // 2. Permitir /puntuacion siempre
        if (comando.equals("/puntuacion")) {
            enviarMensajePrivado(remitente, obtenerReportePuntuacion());
            return;
        }

        // 3. Validación de Turno (Mejorada para Flip Three)
        UnCliente clienteActual = clientesEnSala.get(indiceTurnoActual);
        boolean esTurno = remitente.getClienteID().equals(clienteActual.getClienteID());
        boolean esVictimaFlip3 = (this.flipThreeObjetivo != null && 
                                  remitente.getNombreUsuario().equalsIgnoreCase(this.flipThreeObjetivo.obtenerNombreUsuario()));

        // Si NO es tu turno y NO eres la víctima eligiendo cartas, no puedes enviar mensajes
        if (!esTurno && !esVictimaFlip3) {
            enviarMensajePrivado(remitente, "No es tu turno. Espera a " + clienteActual.getNombreUsuario());
            return;
        }

        Jugador jugadorActual = jugadores.get(remitente.getClienteID());

        // 4. Manejo de Acciones Pendientes (/usar)
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
                if (esVictimaFlip3 && !esTurno) {
                    enviarMensajePrivado(remitente, "Estás en un Flip Three. Solo puedes usar /usar cuando se te pida.");
                    return;
                }
                accionJalar(remitente, jugadorActual);
                break;
            case "/parar":
                if (esVictimaFlip3 && !esTurno) return;
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
            broadcastMensaje("Error: El mazo sigue vacío. Finalizando ronda...");
            finalizarRonda();
            return;
        }

        broadcastMensaje(cliente.getNombreUsuario() + " jaló: " + carta);

        // Si es Acción, se procesa aparte (la lógica de acción decide cuándo pasar turno)
        if (carta.obtenerTipo() == TipoCarta.ACCION) {
            procesarCartaAccion(cliente, jugador, carta);
            return; 
        }

        // Lógica de Second Chance y BUST
        boolean teniaVida = jugador.tieneSecondChance();
        boolean sobrevivio = jugador.intentarJalarCarta(carta);

        if (sobrevivio && teniaVida && !jugador.tieneSecondChance()) {
            broadcastMensaje(" ¡" + cliente.getNombreUsuario() + " usó su SECOND CHANCE para salvarse! La carta " + carta + " fue descartada. ");
        }

        if (!sobrevivio) {
            broadcastMensaje("¡BUST! " + cliente.getNombreUsuario() + " ha perdido la ronda.");
            // Si pierdes, pasa el turno
            siguienteTurno();
        } else {
            // Si sobrevives...
            if (calculadora.verificarFlip7(jugador.obtenerCartasEnMano())) {
                broadcastMensaje("\n¡FLIP 7! " + cliente.getNombreUsuario() + " consiguió 7 cartas únicas.");
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
        UnCliente clienteObj = null;
        for (UnCliente c : clientesEnSala) {
            if (c.getNombreUsuario().equalsIgnoreCase(nombreObjetivo)) {
                clienteObj = c;
                break;
            }
        }

        if (clienteObj == null) {
            enviarMensajePrivado(atacante, "Jugador '" + nombreObjetivo + "' no encontrado. Intenta de nuevo.");
            return;
        }

        Jugador objetivo = jugadores.get(clienteObj.getClienteID());
        String nombreAccion = accionPendiente.toString();

      
        boolean esValido = true;
        String error = "";

        if (objetivo.sePlanto() || objetivo.tieneBUST()) {
            esValido = false;
            error = "Ese jugador ya no está activo en la ronda.";
        }
        if (nombreAccion.equals("Second Chance") && objetivo.tieneSecondChance()) {
            esValido = false;
            error = "Ese jugador YA tiene una Second Chance.";
        }

        if (!esValido) {
            enviarMensajePrivado(atacante, " Error: " + error + " Elige otro.");
            return;
        }
     

        if (nombreAccion.equals("Flip Three")) {
            this.flipThreeCartasRestantes = 3;
            this.flipThreeObjetivo = objetivo;
            this.flipThreeAtacante = atacante;
            this.accionesAcumuladasFlipThree.clear(); 
            this.esperandoObjetivo = false;
            this.accionPendiente = null;
            iniciarOReanudarFlipThree(atacante, objetivo);
            return;

        } else if (nombreAccion.equals("Second Chance") || nombreAccion.equals("Freeze")) {
            if (nombreAccion.equals("Second Chance")) manejadorAcciones.transferirSecondChance(objetivo);
            else if (nombreAccion.equals("Freeze")) manejadorAcciones.aplicarFreeze(objetivo);

            broadcastMensaje("ACCIÓN " + nombreAccion + " ejecutada sobre " + objetivo.obtenerNombreUsuario());

            this.esperandoObjetivo = false;
            this.accionPendiente = null;

            if (this.flipThreeObjetivo != null) {
                procesarSiguienteAccionAcumulada();
            } else {
                siguienteTurno();
            }
        }
    }

    //nuevo metodo flip three corregido
    private void iniciarOReanudarFlipThree(UnCliente atacante, Jugador objetivo) {
        // Limpiamos variables de control inmediatas
        this.esperandoObjetivo = false;
        this.accionPendiente = null;

        // Si es el inicio de un nuevo Flip Three, aseguramos que la lista esté limpia
        if (this.flipThreeCartasRestantes == 3) {
            this.accionesAcumuladasFlipThree.clear();
        }

        boolean rompioBucle = false;

        // BUCLE PRINCIPAL DE ROBO
        while (this.flipThreeCartasRestantes > 0 && !objetivo.tieneBUST()) {
            
            // Regla: Si cumple Flip 7, se detiene todo inmediatamente
            if (calculadora.verificarFlip7(objetivo.obtenerCartasEnMano())) {
                broadcastMensaje("¡FLIP 7 conseguido durante Flip Three! Se detiene la secuencia.");
                finalizarRonda();
                return;
            }

            broadcastMensaje(objetivo.obtenerNombreUsuario() + " jalando carta de Flip Three... (" + this.flipThreeCartasRestantes + " restantes)");
            
            
            Carta carta = baraja.jalarCarta();
            if (carta == null) { baraja.reiniciarBaraja(); carta = baraja.jalarCarta(); }
            if (carta == null) break; // Error extremo

            this.flipThreeCartasRestantes--;

          
            if (carta.obtenerTipo() == TipoCarta.ACCION) {
                String nombre = carta.toString();
                
                if (nombre.equals("Second Chance")) {
                    // Regla: Se la queda inmediatamente. Si ya tiene una, la guarda para darla después.
                    if (!objetivo.tieneSecondChance()) {
                        objetivo.setTieneSecondChance(true);
                        broadcastMensaje(objetivo.obtenerNombreUsuario() + " obtuvo Second Chance (se la queda).");
                    } else {
                        // Si ya tiene, se agrega a la cola para regalarla al final
                        broadcastMensaje("¡Segunda Second Chance! Se guarda para regalar al final.");
                        accionesAcumuladasFlipThree.add(carta);
                    }
                } else {
                    // Regla: Freeze y Flip Three se guardan hasta terminar las 3 cartas
                    broadcastMensaje("Salió " + nombre + ". Se guarda para aplicar al finalizar las 3 cartas.");
                    accionesAcumuladasFlipThree.add(carta);
                }
            } 
           
            else {
                broadcastMensaje("Salió: " + carta.toString());
                boolean sobrevivio = objetivo.intentarJalarCarta(carta); 

                if (!sobrevivio) {
                    broadcastMensaje("¡BUST! " + objetivo.obtenerNombreUsuario() + " perdió todo.");
                    // Regla: Si rompes, ignoras las cartas de acción acumuladas
                    accionesAcumuladasFlipThree.clear();
                    rompioBucle = true;
                    break;
                }
            }
            
            // Pequeña pausa visual para el chat 
            try { Thread.sleep(1000); } catch (InterruptedException e) {}

        } // Fin While

      
        
        if (objetivo.tieneBUST()) {
            // Si murió, turno siguiente.
            this.flipThreeCartasRestantes = 0;
            this.flipThreeObjetivo = null;
            this.flipThreeAtacante = null;
            siguienteTurno();
        } else {
            // Si sobrevivió (cartasRestantes == 0), checamos si hay acciones pendientes 
            if (!accionesAcumuladasFlipThree.isEmpty()) {
                broadcastMensaje("Flip Three finalizado sin BUST. Ahora debes aplicar las acciones acumuladas...");
                procesarSiguienteAccionAcumulada(); 
            } else {
                broadcastMensaje("Flip Three finalizado limpio.");
                this.flipThreeObjetivo = null;
                this.flipThreeAtacante = null;
                siguienteTurno();
            }
        }
    }
    private void procesarSiguienteAccionAcumulada() {
        if (accionesAcumuladasFlipThree.isEmpty()) {
            // Ya no hay más acciones pendientes, terminamos el turno
            this.flipThreeObjetivo = null;
            this.flipThreeAtacante = null;
            siguienteTurno();
            return;
        }

        // Sacamos la primera carta de la lista
        Carta cartaAccion = accionesAcumuladasFlipThree.remove(0);
        this.accionPendiente = cartaAccion;
        this.esperandoObjetivo = true;
        // Usamos al "objetivo" original como el "atacante" de esta nueva acción
        UnCliente ejecutor = null; 
        for(UnCliente c : clientesEnSala) {
            if(c.getClienteID().equals(flipThreeObjetivo.obtenerNombreUsuario())) { 
                 // Buscar el objeto UnCliente correspondiente al Jugador flipThreeObjetivo
                
            }
        }
       
        UnCliente duenoDeLasCartas = buscarClientePorNombre(flipThreeObjetivo.obtenerNombreUsuario());

        if (duenoDeLasCartas != null) {
            // Reutilizamos tu lógica de mostrar menú
            procesarCartaAccion(duenoDeLasCartas, flipThreeObjetivo, cartaAccion);
        }
    }
    
   
    private UnCliente buscarClientePorNombre(String nombre) {
        for(UnCliente c : clientesEnSala) {
            if(c.getNombreUsuario().equals(nombre)) return c;
        }
        return null;
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
            broadcastMensaje("\n ¡JUEGO TERMINADO! ");
            broadcastMensaje("El ganador es: " + ganadorDelJuego.obtenerNombreUsuario().toUpperCase());
            broadcastMensaje("Puntuación Final: " + maxPuntuacionGlobal);
            broadcastMensaje("------------------------------------------");
            broadcastMensaje("Escriban /listo para iniciar una partida nueva.");
        } else {
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
    

    public synchronized void removerJugador(UnCliente cliente) {
    //  Si el juego no ha empezado, solo lo sacamos de la lista
    if (!juegoIniciado) {
        clientesEnSala.remove(cliente);
        jugadores.remove(cliente.getClienteID());
        broadcastMensaje(cliente.getNombreUsuario() + " ha abandonado la sala.");
        return;
    }

    // Si el juego YA empezó, la lógica es diferente
    broadcastMensaje("¡" + cliente.getNombreUsuario() + " abandonó la partida!");

    int indiceJugadorQueSeVa = clientesEnSala.indexOf(cliente);
    if (indiceJugadorQueSeVa == -1) return; // Ya no estaba

    boolean eraSuTurno = (indiceJugadorQueSeVa == indiceTurnoActual);

    // Eliminamos al jugador de las listas
    clientesEnSala.remove(indiceJugadorQueSeVa);
    jugadores.remove(cliente.getClienteID());

    //  Ajustar el índice del turno
    // Si el jugador que se fue estaba ANTES del actual, el índice debe bajar uno para seguir apuntando al mismo jugador que estaba jugando.
    if (indiceJugadorQueSeVa < indiceTurnoActual) {
        indiceTurnoActual--;
    }
    // Si el índice se sale de rango (era el último), lo ajustamos al inicio
    if (indiceTurnoActual >= clientesEnSala.size()) {
        indiceTurnoActual = 0;
    }

    // Si era su turno, pasamos al siguiente automáticamente
    if (eraSuTurno) {
        broadcastMensaje("El jugador actual se fue. Pasando turno...");
        anunciarTurno(); 
    }
  }
}