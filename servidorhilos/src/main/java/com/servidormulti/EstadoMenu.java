package com.servidormulti;

public enum EstadoMenu {
    MENU_PRINCIPAL, // Menú principal: 1. Iniciar Sesión, 2. Registrar, 3. Invitado
    INICIO_SESION_PEDIR_NOMBRE, // Esperando nombre para Iniciar Sesión
    INICIO_SESION_PEDIR_CONTRASENA, // Esperando contraseña para Iniciar Sesión
    REGISTRO_PEDIR_NOMBRE, // Esperando nombre para Registrar
    REGISTRO_PEDIR_CONTRASENA, // Esperando contraseña para Registrar
    REGISTRO_PEDIR_CONFIRMACION, // Esperando confirmación de contraseña
    MENU_CHAT, // Usuario logueado o invitado en modo chat
    MENU_SALA_PRINCIPAL,    // Menú de salas (Opciones: Unirse/Crear/Logout)
    MENU_UNIRSE_SALA,       // Esperando el nombre de la sala para unirse
    MENU_CREAR_SALA_NOMBRE, // Esperando el nombre de la sala a crear
    SALA_ACTIVA,            // El cliente está activamente chateando en una sala
}