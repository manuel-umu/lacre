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
 * Caso de uso de emisión: añade un registro a la cadena una sola vez por clave de idempotencia,
 * en una transacción y bajo un cerrojo consultivo por clave. Vale para el alta y la anulación.
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

    /** El obligado es el emisor de la factura; no se pide aparte. */
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
     * Huella de la petición para la idempotencia: identidad fiscal de la factura más el
     * {@link PeticionRegistro#discriminante()}, no el cuerpo entero.
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

    /** Respuesta guardada para una clave de idempotencia. */
    private record Anotacion(UUID registroId, long posicion, String huella, String huellaPeticion) {

        RespuestaRegistro comoRespuestaSiCoincide(String clave, String huellaPeticion) {
            if (!this.huellaPeticion.equals(huellaPeticion)) {
                throw new ClaveIdempotenciaReutilizadaException(clave);
            }
            return new RespuestaRegistro(registroId, posicion, huella);
        }
    }
}
