package com.codigo2enter.almacenes.modules.auth.service;

import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Lleva el conteo de intentos de login fallidos por usuario en memoria
 * (BUG-INV-17 / CYBER-19 — ASVS V2.2.1: el login no tenía rate limiting,
 * permitiendo ataques de fuerza bruta sin retraso ni bloqueo).
 *
 * Tras {@link #MAX_ATTEMPTS} fallos consecutivos, el usuario queda bloqueado
 * durante {@link #LOCKOUT_DURATION}. Un login exitoso reinicia el contador.
 *
 * El bloqueo es por nombre de usuario (no por IP): es la opción más simple
 * para una app de escala media de un solo nodo y evita depender de cabeceras
 * de IP (potencialmente falsificables tras un proxy). Como contrapartida, un
 * atacante podría bloquear deliberadamente la cuenta de otro usuario — riesgo
 * aceptado y documentado, mitigado por la duración corta del bloqueo.
 */
@Component
public class LoginAttemptService {

    private static final int MAX_ATTEMPTS = 5;
    private static final Duration LOCKOUT_DURATION = Duration.ofMinutes(15);

    private record Attempt(int count, Instant lockedUntil) {}

    private final ConcurrentHashMap<String, Attempt> attempts = new ConcurrentHashMap<>();

    public boolean isBlocked(String username) {
        Attempt attempt = attempts.get(normalize(username));
        if (attempt == null || attempt.lockedUntil() == null) {
            return false;
        }
        if (Instant.now().isAfter(attempt.lockedUntil())) {
            attempts.remove(normalize(username));
            return false;
        }
        return true;
    }

    /** Minutos restantes de bloqueo, redondeados hacia arriba (mínimo 1). */
    public long getRemainingLockoutMinutes(String username) {
        Attempt attempt = attempts.get(normalize(username));
        if (attempt == null || attempt.lockedUntil() == null) {
            return 0;
        }
        Duration remaining = Duration.between(Instant.now(), attempt.lockedUntil());
        if (remaining.isNegative()) {
            return 0;
        }
        long minutes = remaining.toMinutes();
        return remaining.toSecondsPart() > 0 || minutes == 0 ? minutes + 1 : minutes;
    }

    public void loginFailed(String username) {
        attempts.compute(normalize(username), (key, current) -> {
            int count = (current == null ? 0 : current.count()) + 1;
            Instant lockedUntil = count >= MAX_ATTEMPTS ? Instant.now().plus(LOCKOUT_DURATION) : null;
            return new Attempt(count, lockedUntil);
        });
    }

    public void loginSucceeded(String username) {
        attempts.remove(normalize(username));
    }

    private String normalize(String username) {
        return username == null ? "" : username.trim().toLowerCase();
    }
}
