package dev.lacre.verifactu.internal.adaptador;

import dev.lacre.shared.Huella;
import dev.lacre.shared.Nif;
import dev.lacre.shared.ValorInvalidoException;
import dev.lacre.verifactu.registro.RegistroEncadenado;
import dev.lacre.verifactu.registro.TipoRegistro;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.data.annotation.Id;
import org.springframework.data.domain.Persistable;
import org.springframework.data.relational.core.mapping.Table;

/**
 * Fila de la cadena de registros de un obligado: identidad, posición y XML serializado.
 * {@link #isNew()} devuelve siempre {@code true}: la tabla es de solo inserción y nunca se emite
 * un {@code UPDATE}.
 *
 * @param husoOffsetSegundos desplazamiento horario de {@link #fechaHoraHusoGenRegistro}, guardado
 *                           aparte porque {@code timestamptz} normaliza a UTC
 */
@Table("registro_facturacion")
public record RegistroFacturacion(
        @Id UUID id,
        UUID obligadoId,
        long posicion,
        TipoRegistro tipo,
        Nif emisor,
        String numSerieFactura,
        LocalDate fechaExpedicionFactura,
        Huella huella,
        Huella huellaAnterior,
        OffsetDateTime fechaHoraHusoGenRegistro,
        int husoOffsetSegundos,
        String xml)
        implements Persistable<UUID> {

    public RegistroFacturacion {
        if (fechaHoraHusoGenRegistro != null
                && fechaHoraHusoGenRegistro.getOffset().getTotalSeconds() != husoOffsetSegundos) {
            throw new ValorInvalidoException(
                    "El desplazamiento horario guardado no coincide con el de la fecha y hora");
        }
        if (posicion < 1) {
            throw new ValorInvalidoException("La posición en la cadena empieza en 1, y es " + posicion);
        }
    }

    /** Construye el asiento a partir del registro ya encadenado. */
    public static RegistroFacturacion de(
            UUID id, UUID obligadoId, long posicion, RegistroEncadenado registro, String xml) {
        OffsetDateTime fechaHora = registro.fechaHoraHusoGenRegistro();
        return new RegistroFacturacion(
                id,
                obligadoId,
                posicion,
                registro.datos().tipo(),
                registro.datos().idFactura().emisor(),
                registro.datos().idFactura().numSerieFactura(),
                registro.datos().idFactura().fechaExpedicion(),
                registro.huella(),
                registro.registroAnterior().map(anterior -> anterior.huella()).orElse(null),
                fechaHora,
                fechaHora.getOffset().getTotalSeconds(),
                xml);
    }

    /** Fecha y hora con el huso original con el que se calculó la huella. */
    public OffsetDateTime fechaHoraConSuHusoOriginal() {
        return fechaHoraHusoGenRegistro.toInstant().atOffset(java.time.ZoneOffset.ofTotalSeconds(husoOffsetSegundos));
    }

    /** {@code Persistable} exige un getter al estilo JavaBean. */
    @Override
    public UUID getId() {
        return id;
    }

    @Override
    public boolean isNew() {
        return true;
    }
}
