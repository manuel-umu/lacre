package dev.lacre.remision.internal.adaptador;

import dev.lacre.remision.EnvioRechazadoException;
import dev.lacre.remision.EstadoEnvioAeat;
import dev.lacre.remision.EstadoRegistroAeat;
import dev.lacre.remision.LineaRespuesta;
import dev.lacre.remision.RespuestaIlegibleException;
import dev.lacre.remision.RespuestaRemision;
import dev.lacre.shared.Nif;
import dev.lacre.verifactu.registro.IdFactura;
import dev.lacre.verifactu.registro.TipoRegistro;
import java.io.StringReader;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

/** Lee con StAX la respuesta de la AEAT, por nombre local y sin validar contra el XSD. */
final class LectorRespuestaAeat {

    /** {@code sf:fecha}: {@code dd-MM-yyyy}. */
    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd-MM-yyyy");

    /** La AEAT antepone el código al texto del Fault: {@code Codigo[4104].Error en la cabecera}. */
    private static final String PREFIJO_DEL_CODIGO = "Codigo[";

    private LectorRespuestaAeat() {}

    static RespuestaRemision leer(String xml) {
        EstadoEnvioAeat estado = null;
        Duration espera = Duration.ZERO;
        String csv = null;
        List<LineaRespuesta> lineas = new ArrayList<>();

        String faultstring = null;
        Linea enCurso = null;
        try {
            XMLStreamReader lector = entradaSegura().createXMLStreamReader(new StringReader(xml));
            while (lector.hasNext()) {
                if (lector.next() != XMLStreamConstants.START_ELEMENT) {
                    continue;
                }
                String elemento = lector.getLocalName();
                if ("RespuestaLinea".equals(elemento)) {
                    // Una línea se cierra al empezar la siguiente o al acabar el documento: sus
                    // últimos campos son opcionales.
                    if (enCurso != null) {
                        lineas.add(enCurso.aLinea());
                    }
                    enCurso = new Linea();
                    continue;
                }
                if (enCurso == null) {
                    switch (elemento) {
                        case "faultstring" -> faultstring = lector.getElementText();
                        case "CSV" -> csv = lector.getElementText();
                        case "TiempoEsperaEnvio" -> espera = segundos(lector.getElementText());
                        case "EstadoEnvio" -> estado = EstadoEnvioAeat.desde(lector.getElementText());
                        default -> {}
                    }
                    continue;
                }
                switch (elemento) {
                    case "IDEmisorFactura" -> enCurso.emisor = new Nif(lector.getElementText());
                    case "NumSerieFactura" -> enCurso.numSerie = lector.getElementText();
                    case "FechaExpedicionFactura" ->
                        enCurso.fecha = LocalDate.parse(lector.getElementText().trim(), FECHA);
                    case "TipoOperacion" -> enCurso.tipo = tipoDeOperacion(lector.getElementText());
                    case "EstadoRegistro" -> enCurso.estado = EstadoRegistroAeat.desde(lector.getElementText());
                    case "CodigoErrorRegistro" ->
                        enCurso.codigoError =
                                Integer.valueOf(lector.getElementText().trim());
                    case "DescripcionErrorRegistro" -> enCurso.descripcionError = lector.getElementText();
                    // Describe el registro original, con su propio CodigoErrorRegistro.
                    case "RegistroDuplicado" -> saltar(lector);
                    default -> {}
                }
            }
            if (enCurso != null) {
                lineas.add(enCurso.aLinea());
            }
        } catch (XMLStreamException e) {
            throw new RespuestaIlegibleException("no se pudo parsear la respuesta de la AEAT", e);
        }

        // Un rechazo del envío completo llega como SOAP Fault, sin EstadoEnvio ni líneas.
        if (faultstring != null) {
            throw rechazo(faultstring);
        }
        if (estado == null) {
            throw new RespuestaIlegibleException("la respuesta no trae EstadoEnvio");
        }
        return new RespuestaRemision(estado, espera, csv, lineas);
    }

    /** Extrae el código del catálogo del texto del Fault. */
    static EnvioRechazadoException rechazo(String faultstring) {
        String texto = faultstring.trim();
        if (texto.startsWith(PREFIJO_DEL_CODIGO)) {
            int cierre = texto.indexOf(']');
            String digitos = cierre < 0 ? "" : texto.substring(PREFIJO_DEL_CODIGO.length(), cierre);
            if (digitos.chars().allMatch(Character::isDigit) && !digitos.isEmpty()) {
                String resto = texto.substring(cierre + 1);
                return new EnvioRechazadoException(
                        Integer.valueOf(digitos),
                        resto.startsWith(".") ? resto.substring(1).trim() : resto.trim());
            }
        }
        return new EnvioRechazadoException(null, texto);
    }

    /** Avanza hasta el cierre del elemento en curso, con todo lo que contenga. */
    static void saltar(XMLStreamReader lector) throws XMLStreamException {
        int profundidad = 1;
        while (profundidad > 0) {
            switch (lector.next()) {
                case XMLStreamConstants.START_ELEMENT -> profundidad++;
                case XMLStreamConstants.END_ELEMENT -> profundidad--;
                default -> {}
            }
        }
    }

    /** Entidades externas desactivadas: defensa contra XXE. */
    static XMLInputFactory entradaSegura() {
        XMLInputFactory factoria = XMLInputFactory.newInstance();
        factoria.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
        factoria.setProperty(XMLInputFactory.SUPPORT_DTD, false);
        return factoria;
    }

    /** {@code TipoOperacionType} solo admite {@code Alta} y {@code Anulacion}. */
    private static TipoRegistro tipoDeOperacion(String valor) {
        return switch (valor.trim()) {
            case "Alta" -> TipoRegistro.ALTA;
            case "Anulacion" -> TipoRegistro.ANULACION;
            default -> throw new RespuestaIlegibleException("tipo de operación desconocido: " + valor);
        };
    }

    private static Duration segundos(String valor) {
        String limpio = valor.trim();
        return limpio.isEmpty() ? Duration.ZERO : Duration.ofSeconds(Long.parseLong(limpio));
    }

    /** Acumulador de una línea a medio leer. */
    private static final class Linea {
        private Nif emisor;
        private String numSerie;
        private LocalDate fecha;
        private TipoRegistro tipo;
        private EstadoRegistroAeat estado;
        private Integer codigoError;
        private String descripcionError;

        LineaRespuesta aLinea() {
            return new LineaRespuesta(
                    new IdFactura(emisor, numSerie, fecha), tipo, estado, codigoError, descripcionError);
        }
    }
}
