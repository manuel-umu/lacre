package dev.lacre.consola.internal;

import dev.lacre.identidad.ObligadoTributario;
import dev.lacre.remision.EnviosDeObligado;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Comparator;

/** Una fila de la vista general: un obligado, su cadena y sus envíos. */
public record FilaObligado(
        String nif, String nombre, Long ultimaPosicion, EnviosDeObligado envios, Duration esperaMaxima) {

    /** Situación de un obligado, de la que más atención pide a la que menos. */
    public enum Estado {
        ATASCADO("Atascado"),
        CON_ERRORES("Con errores"),
        APARTADOS("Apartados"),
        ENVIANDO("Enviando"),
        AL_DIA("Al día"),
        SIN_ACTIVIDAD("Sin actividad");

        private final String etiqueta;

        Estado(String etiqueta) {
            this.etiqueta = etiqueta;
        }

        public String etiqueta() {
            return etiqueta;
        }
    }

    /** Orden de la vista general: primero los que piden atención y, dentro de cada estado, por nombre. */
    static final Comparator<FilaObligado> POR_ATENCION = Comparator.comparing(FilaObligado::estado)
            .thenComparing(FilaObligado::nombre, String.CASE_INSENSITIVE_ORDER)
            .thenComparing(FilaObligado::nif);

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

    public Estado estado() {
        if (atascado()) {
            return Estado.ATASCADO;
        }
        if (erroresSinAtender() > 0) {
            return Estado.CON_ERRORES;
        }
        if (envios.apartados() > 0) {
            return Estado.APARTADOS;
        }
        if (envios.pendientes() > 0) {
            return Estado.ENVIANDO;
        }
        return ultimaPosicion == null ? Estado.SIN_ACTIVIDAD : Estado.AL_DIA;
    }

    /** Rechazados y aceptados con errores que el operador aún no ha dado por atendidos. */
    public long erroresSinAtender() {
        return envios.aceptadosConErrores() + envios.rechazados() - envios.erroresAtendidos();
    }

    /** Envíos que la AEAT no aceptó sin más: con errores, rechazados y duplicados. */
    public long incidencias() {
        return envios.aceptadosConErrores() + envios.rechazados() + envios.duplicados();
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
