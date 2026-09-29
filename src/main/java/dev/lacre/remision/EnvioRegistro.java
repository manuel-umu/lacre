package dev.lacre.remision;

import dev.lacre.shared.Textos;
import dev.lacre.shared.ValorInvalidoException;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.relational.core.mapping.Table;

/**
 * Fila del outbox: un registro de facturación pendiente de remitir o ya remitido. El estado no
 * se asigna, se transita: los desenlaces solo salen de {@link EstadoEnvio#PENDIENTE}.
 *
 * @param enviadoEn   momento en que respondió la AEAT; nulo mientras está pendiente
 * @param codigoError último error conocido, del catálogo de la AEAT; lo hay también cuando se
 *                    aceptó con errores y cuando un intento falló sin resolver el envío
 */
@Table("envio_registro")
public record EnvioRegistro(
        @Id UUID id,
        UUID registroId,
        UUID obligadoId,
        EstadoEnvio estado,
        OffsetDateTime creadoEn,
        OffsetDateTime enviadoEn,
        Integer codigoError,
        String descripcionError,
        int intentos,
        @Version long version) {

    /** {@code TextMax1500Type} de {@code DescripcionErrorRegistro} en la respuesta de la AEAT. */
    public static final int MAXIMO_LONGITUD_DESCRIPCION_ERROR = 1500;

    public EnvioRegistro {
        if (id == null || registroId == null || obligadoId == null) {
            throw new ValorInvalidoException("El envío necesita identificador, registro y obligado");
        }
        if (estado == null || creadoEn == null) {
            throw new ValorInvalidoException("El envío necesita estado y fecha de creación");
        }
        if (estado.esTerminal() == (enviadoEn == null)) {
            throw new ValorInvalidoException(
                    "Un envío tiene fecha de respuesta si y solo si ha terminado, y este está en " + estado
                            + " con enviadoEn = " + enviadoEn);
        }
        descripcionError =
                Textos.opcional(descripcionError, MAXIMO_LONGITUD_DESCRIPCION_ERROR, "La descripción del error");
    }

    /** Alta en el outbox: pendiente de despachar. */
    public static EnvioRegistro pendiente(UUID id, UUID registroId, UUID obligadoId, OffsetDateTime creadoEn) {
        return new EnvioRegistro(id, registroId, obligadoId, EstadoEnvio.PENDIENTE, creadoEn, null, null, null, 0, 0);
    }

    /** La AEAT lo aceptó sin reparos. */
    public EnvioRegistro aceptado(OffsetDateTime cuando) {
        return resuelto(EstadoEnvio.ACEPTADO, cuando, null, null);
    }

    /** Aceptado y registrado, con un error admisible que hay que subsanar. */
    public EnvioRegistro aceptadoConErrores(OffsetDateTime cuando, int codigo, String descripcion) {
        return resuelto(EstadoEnvio.ACEPTADO_CON_ERRORES, cuando, codigo, descripcion);
    }

    /** La AEAT lo rechazó; se subsana con un registro nuevo. */
    public EnvioRegistro rechazado(OffsetDateTime cuando, int codigo, String descripcion) {
        return resuelto(EstadoEnvio.RECHAZADO, cuando, codigo, descripcion);
    }

    /** La AEAT respondió que ya estaba presentado (código 3000). */
    public EnvioRegistro duplicado(OffsetDateTime cuando, String descripcion) {
        return resuelto(EstadoEnvio.DUPLICADO, cuando, EstadoEnvio.CODIGO_REGISTRO_DUPLICADO, descripcion);
    }

    /**
     * Un intento que no resolvió el envío: la fila sigue pendiente y guarda por qué falló, que
     * es lo único que un integrador tiene para saber si el reintento llegará a alguna parte.
     *
     * @param codigo      del catálogo de la AEAT si el rechazo lo traía; nulo si no se sabe
     * @param descripcion se recorta al máximo que admite la fila: sale de mensajes de excepción,
     *                    que no tienen límite
     */
    public EnvioRegistro otroIntentoFallido(Integer codigo, String descripcion) {
        if (estado.esTerminal()) {
            throw new EnvioYaResueltoException(id, estado, estado);
        }
        return new EnvioRegistro(
                id,
                registroId,
                obligadoId,
                estado,
                creadoEn,
                null,
                codigo,
                recortada(descripcion),
                intentos + 1,
                version);
    }

    private static String recortada(String texto) {
        return texto == null || texto.length() <= MAXIMO_LONGITUD_DESCRIPCION_ERROR
                ? texto
                : texto.substring(0, MAXIMO_LONGITUD_DESCRIPCION_ERROR);
    }

    private EnvioRegistro resuelto(EstadoEnvio desenlace, OffsetDateTime cuando, Integer codigo, String descripcion) {
        if (estado.esTerminal()) {
            throw new EnvioYaResueltoException(id, estado, desenlace);
        }
        if (cuando == null) {
            throw new ValorInvalidoException("Hace falta saber cuándo respondió la AEAT");
        }
        if (cuando.isBefore(creadoEn)) {
            throw new ValorInvalidoException("La AEAT no puede haber respondido antes de que el envío existiera");
        }
        return new EnvioRegistro(
                id, registroId, obligadoId, desenlace, creadoEn, cuando, codigo, descripcion, intentos, version);
    }
}
