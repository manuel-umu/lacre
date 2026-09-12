package dev.lacre.api.internal.consulta;

import dev.lacre.remision.CatalogoErroresAeat;
import dev.lacre.remision.CatalogoErroresAeat.ErrorAeat;
import dev.lacre.remision.EnvioRegistro;
import dev.lacre.verifactu.consulta.RegistroGuardado;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

/**
 * Estado de un registro: su eslabón en la cadena y el desenlace de su remisión.
 *
 * @param huellaAnterior nula en el primer registro de la cadena
 * @param remision       nula solo si el registro no tiene fila de outbox
 */
public record EstadoDelRegistro(
        UUID registroId,
        UUID obligadoId,
        long posicion,
        String tipo,
        String idEmisorFactura,
        String numSerieFactura,
        LocalDate fechaExpedicionFactura,
        String huella,
        String huellaAnterior,
        OffsetDateTime fechaHoraHusoGenRegistro,
        Remision remision) {

    /**
     * @param estado             {@code PENDIENTE} mientras no haya salido; los demás son
     *                           terminales
     * @param codigoError        último error conocido, del catálogo de la AEAT; lo hay también
     *                           cuando se aceptó con errores y cuando un intento falló sin
     *                           resolver el envío
     * @param clasificacionError qué consecuencia tuvo ese código; nula si no está en el catálogo
     * @param intentos           veces que se intentó sin obtener respuesta interpretable
     */
    public record Remision(String estado, OffsetDateTime enviadoEn, Integer codigoError,
                           String clasificacionError, String descripcionError, int intentos) {

        static Remision de(EnvioRegistro envio) {
            Optional<ErrorAeat> error = CatalogoErroresAeat.de(envio.codigoError());
            return new Remision(
                    envio.estado().name(),
                    envio.enviadoEn(),
                    envio.codigoError(),
                    error.map(ErrorAeat::clasificacion).map(Enum::name).orElse(null),
                    descripcion(envio, error),
                    envio.intentos());
        }

        /** La descripción del catálogo solo cubre el hueco: manda la que dio la AEAT. */
        private static String descripcion(EnvioRegistro envio, Optional<ErrorAeat> error) {
            return envio.descripcionError() != null
                    ? envio.descripcionError()
                    : error.map(ErrorAeat::descripcion).orElse(null);
        }
    }

    static EstadoDelRegistro de(RegistroGuardado registro, EnvioRegistro envio) {
        return new EstadoDelRegistro(
                registro.id(),
                registro.obligadoId(),
                registro.posicion(),
                registro.tipo().name(),
                registro.idFactura().emisor().valor(),
                registro.idFactura().numSerieFactura(),
                registro.idFactura().fechaExpedicion(),
                registro.huella().valor(),
                registro.huellaAnterior() == null ? null : registro.huellaAnterior().valor(),
                registro.fechaHoraHusoGenRegistro(),
                envio == null ? null : Remision.de(envio));
    }
}
