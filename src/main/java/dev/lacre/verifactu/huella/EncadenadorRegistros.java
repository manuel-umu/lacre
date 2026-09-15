package dev.lacre.verifactu.huella;

import dev.lacre.verifactu.registro.RegistroAnterior;
import dev.lacre.verifactu.internal.CalculadorHuella;
import dev.lacre.verifactu.registro.DatosRegistro;
import dev.lacre.verifactu.registro.RegistroEncadenado;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.Optional;

/**
 * Enlaza un registro con el anterior de la cadena del obligado y calcula su huella. Servicio de
 * dominio puro, sin Spring ni base de datos. La zona horaria del obligado entra en el cálculo,
 * por eso es argumento de {@link #encadenar} y no del {@link Clock}.
 */
public final class EncadenadorRegistros {

    private final Clock reloj;
    private final Canonicalizador canonicalizador;

    public EncadenadorRegistros(Clock reloj, Canonicalizador canonicalizador) {
        this.reloj = Objects.requireNonNull(reloj, "reloj");
        this.canonicalizador = Objects.requireNonNull(canonicalizador, "canonicalizador");
    }

    /**
     * @param registroAnterior vacío si es el primer registro de la cadena del obligado
     * @param zonaDelObligado zona con la que se fecha el registro; entra en el cálculo de la huella
     */
    public RegistroEncadenado encadenar(DatosRegistro datos, Optional<RegistroAnterior> registroAnterior,
                                        ZoneId zonaDelObligado) {
        Objects.requireNonNull(datos, "datos");
        Objects.requireNonNull(registroAnterior, "registroAnterior");
        Objects.requireNonNull(zonaDelObligado, "zonaDelObligado");

        // Fechado al segundo (art. 10.1.p del RD 1007/2023) antes de calcular la huella.
        OffsetDateTime fechaHoraHusoGenRegistro =
                OffsetDateTime.now(reloj.withZone(zonaDelObligado)).truncatedTo(ChronoUnit.SECONDS);

        String cadenaCanonica = canonicalizador.canonicalizar(
                datos.camposDeHuella(), registroAnterior, fechaHoraHusoGenRegistro);
        return new RegistroEncadenado(datos, registroAnterior, fechaHoraHusoGenRegistro,
                CalculadorHuella.calcular(cadenaCanonica));
    }
}
