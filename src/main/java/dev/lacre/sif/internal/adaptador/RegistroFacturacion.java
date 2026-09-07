package dev.lacre.sif.internal.adaptador;

import dev.lacre.shared.Huella;
import dev.lacre.shared.Nif;
import dev.lacre.shared.ValorInvalidoException;
import dev.lacre.sif.registro.RegistroEncadenado;
import dev.lacre.sif.registro.TipoRegistro;
import org.springframework.data.annotation.Id;
import org.springframework.data.domain.Persistable;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Fila de la cadena de registros de un obligado, tal y como se guarda.
 * <p>
 * No duplica {@link RegistroEncadenado}: aquel es el registro calculado, este es el asiento del
 * libro, con su identidad, su posición en la cadena y el XML ya serializado.
 * <p>
 * {@link #isNew()} devuelve <strong>siempre</strong> {@code true}. Con un {@code @Id} no nulo,
 * Spring Data JDBC asumiría que la fila existe y emitiría un {@code UPDATE}; aquí eso no puede
 * pasar ni por accidente, y si pasara el trigger de la base de datos lo rechazaría.
 *
 * @param husoOffsetSegundos desplazamiento horario de {@link #fechaHoraHusoGenRegistro}, que se
 *                           guarda aparte porque {@code timestamptz} normaliza a UTC y perdería
 *                           el huso con el que se calculó la huella
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
        String xml) implements Persistable<UUID> {

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
    public static RegistroFacturacion de(UUID id, UUID obligadoId, long posicion,
                                         RegistroEncadenado registro, String xml) {
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

    /**
     * Recupera la fecha y hora con el huso original, que es el que entró en el cálculo de la
     * huella. Leer {@link #fechaHoraHusoGenRegistro()} directamente devuelve el mismo instante
     * pero con el desplazamiento que aplique la sesión de base de datos.
     */
    public OffsetDateTime fechaHoraConSuHusoOriginal() {
        return fechaHoraHusoGenRegistro.toInstant()
                .atOffset(java.time.ZoneOffset.ofTotalSeconds(husoOffsetSegundos));
    }

    /** {@code Persistable} pide un getter al estilo JavaBean; el record expone {@code id()}. */
    @Override
    public UUID getId() {
        return id;
    }

    @Override
    public boolean isNew() {
        return true;
    }
}
