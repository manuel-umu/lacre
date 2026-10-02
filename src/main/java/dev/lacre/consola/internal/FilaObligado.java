package dev.lacre.consola.internal;

import dev.lacre.identidad.ObligadoTributario;
import dev.lacre.remision.EnviosDeObligado;
import java.time.Duration;
import java.time.OffsetDateTime;

/** Una fila de la vista general: un obligado, su cadena y sus envíos. */
public record FilaObligado(
        String nif, String nombre, Long ultimaPosicion, EnviosDeObligado envios, Duration esperaMaxima) {

    /** Antigüedad a partir de la cual un pendiente se considera atascado. */
    static final Duration UMBRAL_DE_ATASCO = Duration.ofHours(1);

    static FilaObligado de(
            ObligadoTributario obligado, Long ultimaPosicion, EnviosDeObligado envios, OffsetDateTime ahora) {
        EnviosDeObligado conCeros =
                envios != null ? envios : new EnviosDeObligado(obligado.id(), 0, 0, 0, 0, 0, 0, 0, null);
        Duration espera =
                conCeros.pendienteMasAntiguo() == null ? null : Duration.between(conCeros.pendienteMasAntiguo(), ahora);
        return new FilaObligado(obligado.nif().valor(), obligado.nombreRazon(), ultimaPosicion, conCeros, espera);
    }

    public boolean atascado() {
        return esperaMaxima != null && esperaMaxima.compareTo(UMBRAL_DE_ATASCO) > 0;
    }

    /** La espera del pendiente más antiguo en horas y minutos, o {@code null} si no hay. */
    public String espera() {
        if (esperaMaxima == null) {
            return null;
        }
        long horas = esperaMaxima.toHours();
        return horas > 0
                ? "%d h %d min".formatted(horas, esperaMaxima.toMinutesPart())
                : esperaMaxima.toMinutes() + " min";
    }
}
