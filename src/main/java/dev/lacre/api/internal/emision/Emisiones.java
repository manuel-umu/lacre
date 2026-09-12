package dev.lacre.api.internal.emision;

import dev.lacre.api.internal.emision.RespuestaRegistro.Aviso;
import dev.lacre.identidad.ObligadoDesconocidoException;
import dev.lacre.identidad.ObligadoTributario;
import dev.lacre.identidad.Obligados;
import dev.lacre.shared.Nif;
import dev.lacre.verifactu.emision.AnomaliaPrevia;
import dev.lacre.verifactu.emision.EmisorDeRegistros;
import dev.lacre.verifactu.emision.RegistroEmitido;
import dev.lacre.verifactu.qr.UrlDeCotejo;
import dev.lacre.verifactu.registro.DatosRegistro;
import dev.lacre.verifactu.registro.DatosRegistroAlta;
import dev.lacre.verifactu.registro.DatosRegistroAnulacion;
import dev.lacre.verifactu.registro.SistemaInformatico;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

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
    private final PropiedadesQr qr;

    Emisiones(EmisorDeRegistros emisor, SistemaInformatico sistemaInformatico, Obligados obligados,
              JdbcClient jdbc, Clock reloj, PropiedadesQr qr) {
        this.emisor = emisor;
        this.sistemaInformatico = sistemaInformatico;
        this.obligados = obligados;
        this.jdbc = jdbc;
        this.reloj = reloj;
        this.qr = qr;
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

        DatosRegistro datos = peticion.aDatos(sistemaInformatico);
        String urlQr = urlQrDe(datos);

        RegistroEmitido emitido = emisor.emitir(obligado.id(), datos);
        anotar(obligado.id(), clave, huellaPeticion, emitido, urlQr);
        return new RespuestaRegistro(emitido.id(), emitido.posicion(), emitido.huella().valor(),
                urlQr, avisosDe(emitido.avisos()));
    }

    /** El QR identifica una factura, y una anulación no es una factura que se imprima. */
    private String urlQrDe(DatosRegistro datos) {
        return switch (datos) {
            case DatosRegistroAlta alta -> UrlDeCotejo.de(qr.urlBase(), alta);
            case DatosRegistroAnulacion _ -> null;
        };
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
                select registro_id, posicion, huella, url_qr, huella_peticion, avisos
                from peticion_idempotente
                where obligado_id = :obligado and clave = :clave
                """)
                .param("obligado", obligadoId)
                .param("clave", clave)
                .query((rs, fila) -> new Anotacion(
                        rs.getObject("registro_id", UUID.class),
                        rs.getLong("posicion"),
                        rs.getString("huella"),
                        rs.getString("url_qr"),
                        rs.getString("huella_peticion"),
                        rs.getString("avisos")))
                .optional();
    }

    private void anotar(UUID obligadoId, String clave, String huellaPeticion,
                        RegistroEmitido emitido, String urlQr) {
        jdbc.sql("""
                insert into peticion_idempotente
                    (obligado_id, clave, huella_peticion, registro_id, posicion, huella, url_qr,
                     avisos, creado_en)
                values (:obligado, :clave, :huellaPeticion, :registro, :posicion, :huella, :urlQr,
                        :avisos, :creadoEn)
                """)
                .param("obligado", obligadoId)
                .param("clave", clave)
                .param("huellaPeticion", huellaPeticion)
                .param("registro", emitido.id())
                .param("posicion", emitido.posicion())
                .param("huella", emitido.huella().valor())
                .param("urlQr", urlQr)
                .param("avisos", codificar(emitido.avisos()))
                .param("creadoEn", OffsetDateTime.now(reloj))
                .update();
    }

    private static List<Aviso> avisosDe(Set<AnomaliaPrevia> anomalias) {
        return anomalias.stream().map(Aviso::de).toList();
    }

    /** Los avisos se anotan por su código, separados por comas; nulo si no hubo ninguno. */
    private static String codificar(Set<AnomaliaPrevia> anomalias) {
        return anomalias.isEmpty() ? null
                : anomalias.stream().map(AnomaliaPrevia::name).collect(Collectors.joining(","));
    }

    private static List<Aviso> descodificar(String codigos) {
        return codigos == null ? List.of()
                : Arrays.stream(codigos.split(","))
                        .map(codigo -> Aviso.de(AnomaliaPrevia.valueOf(codigo)))
                        .toList();
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
    private record Anotacion(UUID registroId, long posicion, String huella, String urlQr,
                             String huellaPeticion, String avisos) {

        RespuestaRegistro comoRespuestaSiCoincide(String clave, String huellaPeticion) {
            if (!this.huellaPeticion.equals(huellaPeticion)) {
                throw new ClaveIdempotenciaReutilizadaException(clave);
            }
            return new RespuestaRegistro(registroId, posicion, huella, urlQr,
                    descodificar(avisos));
        }
    }
}
