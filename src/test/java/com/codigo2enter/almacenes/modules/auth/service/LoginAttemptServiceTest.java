package com.codigo2enter.almacenes.modules.auth.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class LoginAttemptServiceTest {

    private final LoginAttemptService service = new LoginAttemptService();

    @Test
    void usuarioSinIntentos_noEstaBloqueado() {
        assertFalse(service.isBlocked("nuevo"));
        assertEquals(0, service.getRemainingLockoutMinutes("nuevo"));
    }

    @Test
    void menosDeCincoFallos_noBloquea() {
        for (int i = 0; i < 4; i++) {
            service.loginFailed("tester");
        }
        assertFalse(service.isBlocked("tester"));
    }

    @Test
    void cincoFallosConsecutivos_bloquea() {
        for (int i = 0; i < 5; i++) {
            service.loginFailed("tester");
        }
        assertTrue(service.isBlocked("tester"));
        assertTrue(service.getRemainingLockoutMinutes("tester") > 0);
    }

    @Test
    void loginExitoso_reiniciaContador() {
        for (int i = 0; i < 4; i++) {
            service.loginFailed("tester");
        }
        service.loginSucceeded("tester");
        service.loginFailed("tester");

        assertFalse(service.isBlocked("tester"));
    }

    @Test
    void usernameEsCaseInsensitive() {
        for (int i = 0; i < 5; i++) {
            service.loginFailed("Admin");
        }
        assertTrue(service.isBlocked("admin"));
        assertTrue(service.isBlocked("ADMIN"));
    }

    @Test
    void bloqueoDeUnUsuario_noAfectaAOtro() {
        for (int i = 0; i < 5; i++) {
            service.loginFailed("tester");
        }
        assertTrue(service.isBlocked("tester"));
        assertFalse(service.isBlocked("otro"));
    }
}
