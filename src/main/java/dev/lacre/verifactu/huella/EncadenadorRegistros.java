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
 * Enlaza un registro de alta con el anterior de la cadena del obligado.
 * <p>
 * Servicio de dominio puro: sin Spring, sin base de datos y sin acceso al reloj del sistema.
 * El instante de generación entra en el cálculo de la huella, así que con el mismo
 * {@link Clock} y los mismos datos la cadena es reproducible.
 * <p>
 * La zona importa: la huella se calcula sobre la fecha y hora <em>con huso</em>, de modo que el
 * mismo instante en dos zonas distintas produce huellas distintas. Por eso la zona es argumento
 * de {@link #encadenar} y no del reloj: es dato del obligado por cuya cuenta se factura, y una
 * misma instalación factura para obligados peninsulares y canarios. La zona del {@code Clock}
 * inyectado no se usa.
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

        // El registro se fecha al segundo (art. 10.1.p del RD 1007/2023). Truncar aquí, y no al
        // formatear, evita que lo que se firma y lo que se guarda difieran en los nanosegundos.
        OffsetDateTime fechaHoraHusoGenRegistro =
                OffsetDateTime.now(reloj.withZone(zonaDelObligado)).truncatedTo(ChronoUnit.SECONDS);

        String cadenaCanonica = canonicalizador.canonicalizar(datos, registroAnterior, fechaHoraHusoGenRegistro);
        return new RegistroEncadenado(datos, registroAnterior, fechaHoraHusoGenRegistro,
                CalculadorHuella.calcular(cadenaCanonica));
    }
}
