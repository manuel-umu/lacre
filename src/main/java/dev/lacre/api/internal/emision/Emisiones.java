package dev.lacre.api.internal.emision;

import dev.lacre.identidad.ObligadoDesconocidoException;
import dev.lacre.identidad.ObligadoTributario;
import dev.lacre.identidad.Obligados;
import dev.lacre.shared.Nif;
import dev.lacre.verifactu.emision.EmisorDeRegistros;
import dev.lacre.verifactu.emision.RegistroEmitido;
import dev.lacre.verifactu.registro.SistemaInformatico;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

/**
 * Añade un registro a la cadena, una sola vez por clave de idempotencia. Vale para el alta y
 * para la anulación: para este servicio son el mismo caso de uso con distinto contenido.
 * <p>
 * Un ERP reintenta: por un timeout, por un reinicio, porque su cliente HTTP lo hace solo. Y un
 * reintento no puede añadir un segundo eslabón a la cadena con los mismos datos, porque la
 * cadena es de solo inserción y lo único que quedaría después sería anular uno de los dos.
 * <p>
 * <strong>Todo ocurre en una transacción</strong>, y empieza tomando un cerrojo consultivo por
 * clave. Es el mismo mecanismo que usa {@code CadenaDeRegistros} para serializar la cadena, y
 * por la misma razón: sin él, dos reintentos simultáneos leerían los dos que no hay anotación
 * y emitirían los dos. La clave primaria de la tabla pararía al segundo, pero perdiendo la
 * transacción entera en vez de devolverle la respuesta que ya existía.
 * <p>
 * El orden de los dos cerrojos es siempre el mismo —primero la clave, después la cadena—, que
 * es lo que evita que dos peticiones cruzadas se bloqueen mutuamente.
 */
@Service
class Emisiones {

    private final EmisorDeRegistros emisor;
    private final SistemaInformatico sistemaInformatico;
    private final Obligados obligados;
    private final JdbcClient jdbc;
    private final Clock reloj;

    Emisiones(EmisorDeRegistros emisor, SistemaInformatico sistemaInformatico, Obligados obligados,
              JdbcClient jdbc, Clock reloj) {
        this.emisor = emisor;
        this.sistemaInformatico = sistemaInformatico;
        this.obligados = obligados;
        this.jdbc = jdbc;
        this.reloj = reloj;
    }

    @Transactional
    RespuestaRegistro emitir(String clave, PeticionRegistro peticion) {
        ObligadoTributario obligado = obligadoDe(peticion);
        String huellaPeticion = huellaDe(peticion);

        serializarLaClave(obligado.id(), clave);

        Optional<Anotacion> anotada = anotacion(obligado.id(), clave);
        if (anotada.isPresent()) {
            return anotada.get().comoRespuestaSiCoincide(clave, huellaPeticion);
        }

        RegistroEmitido emitido = emisor.emitir(obligado.id(), peticion.aDatos(sistemaInformatico));
        anotar(obligado.id(), clave, huellaPeticion, emitido);
        return new RespuestaRegistro(emitido.id(), emitido.posicion(), emitido.huella().valor());
    }

    /**
     * El obligado no se pide en el cuerpo: en Veri*Factu el emisor de la factura <em>es</em> el
     * obligado por cuya cuenta se expide, tanto al expedirla como al anularla. Pedirlo aparte
     * solo añadiría una forma de que los dos no coincidieran.
     */
    private ObligadoTributario obligadoDe(PeticionRegistro peticion) {
        Nif emisorFactura = new Nif(peticion.factura().idEmisorFactura());
        return obligados.findByNif(emisorFactura)
                .orElseThrow(() -> new ObligadoDesconocidoException(emisorFactura));
    }

    private void serializarLaClave(UUID obligadoId, String clave) {
        jdbc.sql("select pg_advisory_xact_lock(hashtext(:clave))")
                .param("clave", "idempotencia:" + obligadoId + ":" + clave)
                .query()
                .singleRow();
    }

    private Optional<Anotacion> anotacion(UUID obligadoId, String clave) {
        return jdbc.sql("""
                select registro_id, posicion, huella, huella_peticion
                from peticion_idempotente
                where obligado_id = :obligado and clave = :clave
                """)
                .param("obligado", obligadoId)
                .param("clave", clave)
                .query((rs, fila) -> new Anotacion(
                        rs.getObject("registro_id", UUID.class),
                        rs.getLong("posicion"),
                        rs.getString("huella"),
                        rs.getString("huella_peticion")))
                .optional();
    }

    private void anotar(UUID obligadoId, String clave, String huellaPeticion, RegistroEmitido emitido) {
        jdbc.sql("""
                insert into peticion_idempotente
                    (obligado_id, clave, huella_peticion, registro_id, posicion, huella, creado_en)
                values (:obligado, :clave, :huellaPeticion, :registro, :posicion, :huella, :creadoEn)
                """)
                .param("obligado", obligadoId)
                .param("clave", clave)
                .param("huellaPeticion", huellaPeticion)
                .param("registro", emitido.id())
                .param("posicion", emitido.posicion())
                .param("huella", emitido.huella().valor())
                .param("creadoEn", OffsetDateTime.now(reloj))
                .update();
    }

    /**
     * Identidad fiscal de lo que se pide, no el cuerpo entero del JSON.
     * <p>
     * Se eligió así a propósito. Lo que hay que detectar es que alguien reutilice una clave para
     * <strong>otra factura</strong>, porque devolverle entonces el registro de la primera le
     * haría dar por registrada —o por anulada— una factura que no lo está. Que un reintento
     * cambie la descripción de la operación o el orden de un campo no es eso: sigue siendo la
     * misma petición y merece la misma respuesta, no un 409 por una diferencia de bytes.
     * <p>
     * El tipo entra por {@link PeticionRegistro#discriminante()}, y no por un {@code switch}
     * sobre la clase: la misma factura puede tener alta y anulación, y usar la misma clave para
     * las dos es un error del integrador que debe ver. Cada petición sabe qué la distingue.
     */
    private static String huellaDe(PeticionRegistro peticion) {
        IdFacturaDto id = peticion.factura();
        return sha256(String.join("|",
                String.valueOf(id.idEmisorFactura()),
                String.valueOf(id.numSerieFactura()),
                String.valueOf(id.fechaExpedicionFactura()),
                peticion.discriminante()));
    }

    private static String sha256(String texto) {
        try {
            byte[] resumen = MessageDigest.getInstance("SHA-256")
                    .digest(texto.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().withUpperCase().formatHex(resumen);
        } catch (NoSuchAlgorithmException imposible) {
            throw new IllegalStateException("SHA-256 es obligatorio en toda JVM", imposible);
        }
    }

    /** Lo que se respondió a una clave, tal y como quedó guardado. */
    private record Anotacion(UUID registroId, long posicion, String huella, String huellaPeticion) {

        RespuestaRegistro comoRespuestaSiCoincide(String clave, String huellaPeticion) {
            if (!this.huellaPeticion.equals(huellaPeticion)) {
                throw new ClaveIdempotenciaReutilizadaException(clave);
            }
            return new RespuestaRegistro(registroId, posicion, huella);
        }
    }
}
