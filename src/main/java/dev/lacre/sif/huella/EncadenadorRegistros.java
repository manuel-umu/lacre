package dev.lacre.sif.huella;

import dev.lacre.sif.registro.RegistroAnterior;
import dev.lacre.sif.internal.CalculadorHuella;
import dev.lacre.sif.registro.DatosRegistro;
import dev.lacre.sif.registro.RegistroEncadenado;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.Optional;

/**
 * Enlaza un registro de alta con el anterior de la cadena del obligado.
 * <p>
 * Servicio de dominio puro: sin Spring, sin base de datos y sin acceso al reloj del sistema.
 * El instante de generación entra en el cálculo de la huella, así que con el mismo
 * {@link Clock} y los mismos datos la cadena es reproducible.
 * <p>
 * La zona del {@code Clock} importa: la huella se calcula sobre la fecha y hora <em>con
 * huso</em>, de modo que el mismo instante en dos zonas distintas produce huellas distintas.
 * El reloj debe llevar la zona del obligado.
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
     */
    public RegistroEncadenado encadenar(DatosRegistro datos, Optional<RegistroAnterior> registroAnterior) {
        Objects.requireNonNull(datos, "datos");
        Objects.requireNonNull(registroAnterior, "registroAnterior");

        // El registro se fecha al segundo (art. 10.1.p del RD 1007/2023). Truncar aquí, y no al
        // formatear, evita que lo que se firma y lo que se guarda difieran en los nanosegundos.
        OffsetDateTime fechaHoraHusoGenRegistro =
                OffsetDateTime.now(reloj).truncatedTo(ChronoUnit.SECONDS);

        String cadenaCanonica = canonicalizador.canonicalizar(datos, registroAnterior, fechaHoraHusoGenRegistro);
        return new RegistroEncadenado(datos, registroAnterior, fechaHoraHusoGenRegistro,
                CalculadorHuella.calcular(cadenaCanonica));
    }
}
