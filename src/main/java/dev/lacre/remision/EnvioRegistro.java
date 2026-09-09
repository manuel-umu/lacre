package dev.lacre.remision;

import dev.lacre.shared.Textos;
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
 * identificador existe es la clave ajena, y quien impide que un registro tenga dos envíos es el
 * {@code UNIQUE} de esa misma columna.
 * <p>
 * Lleva también el obligado, que ya viaja en el evento {@code RegistroCreado}. Es dato repetido
 * respecto de {@code registro_facturacion}, y a cambio el despachador agrupa sus lotes sin
 * consultar la tabla de otro módulo en cada pasada.
 * <p>
 * Lleva {@code @Version} porque sí se modifica —el estado cambia con lo que responda la AEAT—,
 * al contrario que {@code RegistroFacturacion}, que es de solo inserción.
 * <p>
 * <strong>El estado no se asigna, se transita.</strong> No hay forma de escribir un estado
 * arbitrario: los cuatro desenlaces se producen con los métodos de abajo, que solo salen de
 * {@link EstadoEnvio#PENDIENTE}. Un envío ya resuelto no cambia de opinión.
 *
 * @param enviadoEn momento en que la AEAT respondió; nulo mientras está pendiente
 * @param codigoError código del catálogo de la AEAT, presente también cuando se aceptó con
 *                    errores, porque ahí queda algo que subsanar
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

    public static final int MAXIMO_LONGITUD_DESCRIPCION_ERROR = 500;

    public EnvioRegistro {
        if (id == null || registroId == null || obligadoId == null) {
            throw new ValorInvalidoException(
                    "El envío necesita identificador, registro y obligado");
        }
        if (estado == null || creadoEn == null) {
            throw new ValorInvalidoException("El envío necesita estado y fecha de creación");
        }
        if (estado.esTerminal() == (enviadoEn == null)) {
            throw new ValorInvalidoException(
                    "Un envío tiene fecha de respuesta si y solo si ha terminado, y este está en "
                            + estado + " con enviadoEn = " + enviadoEn);
        }
        descripcionError = Textos.opcional(
                descripcionError, MAXIMO_LONGITUD_DESCRIPCION_ERROR, "La descripción del error");
    }

    /** Alta en el outbox: pendiente de despachar. */
    public static EnvioRegistro pendiente(UUID id, UUID registroId, UUID obligadoId,
                                          OffsetDateTime creadoEn) {
        return new EnvioRegistro(id, registroId, obligadoId, EstadoEnvio.PENDIENTE, creadoEn,
                null, null, null, 0, 0);
    }

    /** La AEAT lo aceptó sin reparos. */
    public EnvioRegistro aceptado(OffsetDateTime cuando) {
        return resuelto(EstadoEnvio.ACEPTADO, cuando, null, null);
    }

    /**
     * Aceptado y registrado, pero con un error admisible que hay que subsanar. El código es
     * obligatorio: sin él nadie sabría qué subsanar, que es lo único que este estado significa.
     */
    public EnvioRegistro aceptadoConErrores(OffsetDateTime cuando, int codigo, String descripcion) {
        return resuelto(EstadoEnvio.ACEPTADO_CON_ERRORES, cuando, codigo, descripcion);
    }

    /** La AEAT lo rechazó. Se arregla generando un registro nuevo, no reenviando este. */
    public EnvioRegistro rechazado(OffsetDateTime cuando, int codigo, String descripcion) {
        return resuelto(EstadoEnvio.RECHAZADO, cuando, codigo, descripcion);
    }

    /** La AEAT respondió que ya estaba presentado (código 3000). */
    public EnvioRegistro duplicado(OffsetDateTime cuando, String descripcion) {
        return resuelto(EstadoEnvio.DUPLICADO, cuando,
                EstadoEnvio.CODIGO_REGISTRO_DUPLICADO, descripcion);
    }

    /**
     * Un intento que no obtuvo respuesta interpretable. La fila sigue pendiente: no sabemos si la
     * AEAT llegó a registrar el lote, así que lo único honesto es reintentar.
     */
    public EnvioRegistro otroIntentoFallido() {
        if (estado.esTerminal()) {
            throw new EnvioYaResueltoException(id, estado, estado);
        }
        return new EnvioRegistro(id, registroId, obligadoId, estado, creadoEn, null, null, null,
                intentos + 1, version);
    }

    private EnvioRegistro resuelto(EstadoEnvio desenlace, OffsetDateTime cuando,
                                   Integer codigo, String descripcion) {
        if (estado.esTerminal()) {
            throw new EnvioYaResueltoException(id, estado, desenlace);
        }
        if (cuando == null) {
            throw new ValorInvalidoException("Hace falta saber cuándo respondió la AEAT");
        }
        if (cuando.isBefore(creadoEn)) {
            throw new ValorInvalidoException(
                    "La AEAT no puede haber respondido antes de que el envío existiera");
        }
        return new EnvioRegistro(id, registroId, obligadoId, desenlace, creadoEn, cuando,
                codigo, descripcion, intentos, version);
    }
}
