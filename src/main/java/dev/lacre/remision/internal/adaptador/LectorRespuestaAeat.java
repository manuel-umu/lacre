package dev.lacre.remision.internal.adaptador;

import dev.lacre.remision.EstadoEnvioAeat;
import dev.lacre.remision.EstadoRegistroAeat;
import dev.lacre.remision.LineaRespuesta;
import dev.lacre.remision.RespuestaIlegibleException;
import dev.lacre.remision.RespuestaRemision;
import dev.lacre.shared.Nif;
import dev.lacre.verifactu.registro.IdFactura;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import java.io.StringReader;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Lee la respuesta de la AEAT con StAX, en la misma línea que el
 * <a href="../../../../../../docs/adr/0003-serializacion-xml-con-stax.md">ADR 0003</a>: cero
 * dependencias nuevas y el orden del esquema a la vista.
 * <p>
 * Va por <strong>nombre local</strong>, ignorando prefijos: los que use la AEAT en su respuesta
 * son cosa suya y podrían cambiar sin cambiar el esquema.
 * <p>
 * No valida contra el XSD al leer. Si la AEAT devolviera algo que el esquema no admite, el
 * problema sería suyo y aquí solo cabría dejar constancia; validar la entrada nos haría descartar
 * una respuesta que quizá es la única prueba de una presentación.
 */
final class LectorRespuestaAeat {

    /**
     * El {@code sf:fecha} de la AEAT, {@code dd-MM-yyyy}, que el XSD fija con el patrón
     * {@code \d{2}-\d{2}-\d{4}}.
     * <p>
     * Está repetido a propósito y no reutiliza {@code FormatosAeat}: ese vive en
     * {@code verifactu.internal} y este módulo tiene prohibido entrar ahí, que es la frontera
     * que permite extraer el núcleo como librería. La duplicación es asumible porque aquí se
     * <em>lee</em>: un desajuste de formato lanzaría al parsear, no produciría un valor
     * incorrecto en silencio, que es el riesgo del que avisa el ADR 0003.
     */
    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd-MM-yyyy");

    private LectorRespuestaAeat() {
    }

    static RespuestaRemision leer(String xml) {
        EstadoEnvioAeat estado = null;
        Duration espera = Duration.ZERO;
        String csv = null;
        List<LineaRespuesta> lineas = new ArrayList<>();

        Linea enCurso = null;
        try {
            XMLStreamReader lector = entradaSegura().createXMLStreamReader(new StringReader(xml));
            while (lector.hasNext()) {
                if (lector.next() != XMLStreamConstants.START_ELEMENT) {
                    continue;
                }
                String elemento = lector.getLocalName();
                if ("RespuestaLinea".equals(elemento)) {
                    // Una línea se cierra cuando empieza la siguiente: el código y la descripción
                    // del error son opcionales y van al final, así que no hay campo que marque su
                    // fin. El resto lo cierra el final del documento, más abajo.
                    if (enCurso != null) {
                        lineas.add(enCurso.aLinea());
                    }
                    enCurso = new Linea();
                    continue;
                }
                if (enCurso == null) {
                    switch (elemento) {
                        case "CSV" -> csv = lector.getElementText();
                        case "TiempoEsperaEnvio" -> espera = segundos(lector.getElementText());
                        case "EstadoEnvio" -> estado = EstadoEnvioAeat.desde(lector.getElementText());
                        default -> { }
                    }
                    continue;
                }
                switch (elemento) {
                    case "IDEmisorFactura" -> enCurso.emisor = new Nif(lector.getElementText());
                    case "NumSerieFactura" -> enCurso.numSerie = lector.getElementText();
                    case "FechaExpedicionFactura" ->
                            enCurso.fecha = LocalDate.parse(lector.getElementText().trim(), FECHA);
                    case "EstadoRegistro" ->
                            enCurso.estado = EstadoRegistroAeat.desde(lector.getElementText());
                    case "CodigoErrorRegistro" ->
                            enCurso.codigoError = Integer.valueOf(lector.getElementText().trim());
                    case "DescripcionErrorRegistro" -> enCurso.descripcionError = lector.getElementText();
                    default -> { }
                }
            }
            if (enCurso != null) {
                lineas.add(enCurso.aLinea());
            }
        } catch (XMLStreamException e) {
            throw new RespuestaIlegibleException("no se pudo parsear la respuesta de la AEAT", e);
        }

        if (estado == null) {
            throw new RespuestaIlegibleException("la respuesta no trae EstadoEnvio");
        }
        return new RespuestaRemision(estado, espera, csv, lineas);
    }

    /**
     * Una respuesta viene de fuera, así que se parsea con las entidades externas desactivadas:
     * es la defensa estándar contra XXE, y no cuesta nada.
     */
    private static XMLInputFactory entradaSegura() {
        XMLInputFactory factoria = XMLInputFactory.newInstance();
        factoria.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
        factoria.setProperty(XMLInputFactory.SUPPORT_DTD, false);
        return factoria;
    }

    private static Duration segundos(String valor) {
        String limpio = valor.trim();
        return limpio.isEmpty() ? Duration.ZERO : Duration.ofSeconds(Long.parseLong(limpio));
    }

    /** Acumulador mutable de una línea a medio leer. El record inmutable se crea al cerrarla. */
    private static final class Linea {
        private Nif emisor;
        private String numSerie;
        private LocalDate fecha;
        private EstadoRegistroAeat estado;
        private Integer codigoError;
        private String descripcionError;

        LineaRespuesta aLinea() {
            return new LineaRespuesta(new IdFactura(emisor, numSerie, fecha), estado,
                    codigoError, descripcionError);
        }
    }
}
