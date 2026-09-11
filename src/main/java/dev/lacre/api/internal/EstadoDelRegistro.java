package dev.lacre.api.internal;

import dev.lacre.remision.EnvioRegistro;
import dev.lacre.verifactu.consulta.RegistroGuardado;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * En qué quedó un registro: dónde está en la cadena y qué dijo la AEAT.
 * <p>
 * Cruza dos módulos —{@code verifactu} pone el eslabón y {@code remision} el desenlace— y por
 * eso se compone aquí, que es el único sitio que ve a los dos. Cada uno se consulta por su API
 * publicada; nadie lee la tabla del otro.
 *
 * @param huellaAnterior nula solo en el primer registro de la cadena
 * @param remision nula solo si el registro se guardó sin su fila de outbox, cosa que la
 *                 transacción de emisión hace imposible: si aparece, es que alguien tocó la
 *                 base de datos a mano
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
     * @param estado {@code PENDIENTE} mientras no haya salido; los otros cuatro son terminales
     * @param codigoError del catálogo de la AEAT. Presente también cuando se aceptó con errores,
     *                    porque ahí queda algo que subsanar
     * @param intentos veces que se intentó sin obtener respuesta interpretable
     */
    public record Remision(String estado, OffsetDateTime enviadoEn, Integer codigoError,
                           String descripcionError, int intentos) {

        static Remision de(EnvioRegistro envio) {
            return new Remision(envio.estado().name(), envio.enviadoEn(), envio.codigoError(),
                    envio.descripcionError(), envio.intentos());
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
