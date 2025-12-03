package com.servidormulti;

public enum EstadoMenu {
    MENU_PRINCIPAL, // Menú principal: 1. Iniciar Sesión, 2. Registrar, 3. Invitado
    INICIO_SESION_PEDIR_NOMBRE, // Esperando nombre para Iniciar Sesión
    INICIO_SESION_PEDIR_CONTRASENA, // Esperando contraseña para Iniciar Sesión
    REGISTRO_PEDIR_NOMBRE, // Esperando nombre para Registrar
    REGISTRO_PEDIR_CONTRASENA, // Esperando contraseña para Registrar
    REGISTRO_PEDIR_CONFIRMACION, // Esperando confirmación de contraseña
    MENU_LOBBY, // Usuario logueado o invitado en modo chat
}