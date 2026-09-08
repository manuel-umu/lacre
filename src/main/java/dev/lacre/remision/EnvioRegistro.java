package dev.lacre.remision;

import dev.lacre.shared.ValorInvalidoException;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.relational.core.mapping.Table;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Fila del outbox: un registro de facturación pendiente de remitir, o ya remitido.
 * <p>
 * Referencia al registro por {@code UUID} pelado y no con {@code AggregateReference}: el tipo
 * del agregado vive en {@code verifactu.internal.adaptador} y este módulo tiene prohibido
 * verlo, que es justo la frontera que permite extraer el núcleo. Quien garantiza que el
 * identificador existe es la clave ajena.
 * <p>
 * Lleva {@code @Version} porque sí se modifica —el estado cambia con lo que responda la AEAT—,
 * al contrario que {@code RegistroFacturacion}, que es de solo inserción.
 */
@Table("envio_registro")
public record EnvioRegistro(
        @Id UUID id,
        UUID registroId,
        EstadoEnvio estado,
        OffsetDateTime creadoEn,
        @Version long version) {

    public EnvioRegistro {
        if (id == null || registroId == null) {
            throw new ValorInvalidoException("El envío necesita identificador y registro");
        }
        if (estado == null || creadoEn == null) {
            throw new ValorInvalidoException("El envío necesita estado y fecha de creación");
        }
    }

    /** Alta en el outbox: pendiente de despachar. */
    public static EnvioRegistro pendiente(UUID id, UUID registroId, OffsetDateTime creadoEn) {
        return new EnvioRegistro(id, registroId, EstadoEnvio.PENDIENTE, creadoEn, 0);
    }
}
