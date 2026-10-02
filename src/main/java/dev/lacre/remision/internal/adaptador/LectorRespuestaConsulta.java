package dev.lacre.remision.internal.adaptador;

import dev.lacre.remision.RegistroEnAeat;
import dev.lacre.remision.RespuestaIlegibleException;
import dev.lacre.shared.Huella;
import dev.lacre.shared.Nif;
import dev.lacre.shared.ValorInvalidoException;
import dev.lacre.verifactu.registro.IdFactura;
import java.io.StringReader;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

/**
 * Lee con StAX la respuesta de {@code ConsultaFactuSistemaFacturacion}, por estructura y no por
 * nombre: {@code NumSerieFactura} y {@code Huella} aparecen también en el encadenamiento y en las
 * facturas rectificadas.
 */
final class LectorRespuestaConsulta {

    /** {@code sf:fecha}: {@code dd-MM-yyyy}. */
    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd-MM-yyyy");

    /**
     * Una página de la consulta.
     *
     * @param siguiente la clave con la que pedir la página siguiente, o nula si no hay más
     */
    record Pagina(List<RegistroEnAeat> registros, IdFactura siguiente) {}

    private LectorRespuestaConsulta() {}

    static Pagina leer(String xml) {
        List<RegistroEnAeat> registros = new ArrayList<>();
        String faultstring = null;
        String indicadorPaginacion = null;
        String resultado = null;
        IdFactura clave = null;
        try {
            XMLStreamReader lector = LectorRespuestaAeat.entradaSegura().createXMLStreamReader(new StringReader(xml));
            while (lector.hasNext()) {
                if (lector.next() != XMLStreamConstants.START_ELEMENT) {
                    continue;
                }
                switch (lector.getLocalName()) {
                    case "faultstring" -> faultstring = lector.getElementText();
                    case "IndicadorPaginacion" ->
                        indicadorPaginacion = lector.getElementText().trim();
                    case "ResultadoConsulta" ->
                        resultado = lector.getElementText().trim();
                    case "RegistroRespuestaConsultaFactuSistemaFacturacion" -> registros.add(registro(lector));
                    case "ClavePaginacion" -> clave = idFactura(lector);
                    case "Cabecera", "PeriodoImputacion" -> LectorRespuestaAeat.saltar(lector);
                    default -> {}
                }
            }
        } catch (XMLStreamException | DateTimeException | ValorInvalidoException | NumberFormatException e) {
            throw new RespuestaIlegibleException("no se pudo parsear la respuesta de la consulta", e);
        }

        if (faultstring != null) {
            throw LectorRespuestaAeat.rechazo(faultstring);
        }
        if (resultado == null || indicadorPaginacion == null) {
            throw new RespuestaIlegibleException("la respuesta no trae ResultadoConsulta ni IndicadorPaginacion");
        }
        if ("S".equals(indicadorPaginacion) && clave == null) {
            throw new RespuestaIlegibleException("la respuesta anuncia más páginas sin ClavePaginacion");
        }
        return new Pagina(registros, "S".equals(indicadorPaginacion) ? clave : null);
    }

    private static RegistroEnAeat registro(XMLStreamReader lector) throws XMLStreamException {
        Registro registro = new Registro();
        hijos(lector, hijo -> {
            switch (hijo) {
                case "IDFactura" -> registro.idFactura = idFactura(lector);
                case "DatosRegistroFacturacion" ->
                    hijos(lector, dato -> {
                        if ("Huella".equals(dato)) {
                            registro.huella = new Huella(lector.getElementText().trim());
                        } else {
                            LectorRespuestaAeat.saltar(lector);
                        }
                    });
                case "DatosPresentacion" ->
                    hijos(lector, dato -> {
                        if ("TimestampPresentacion".equals(dato)) {
                            registro.presentado =
                                    OffsetDateTime.parse(lector.getElementText().trim());
                        } else {
                            LectorRespuestaAeat.saltar(lector);
                        }
                    });
                case "EstadoRegistro" ->
                    hijos(lector, dato -> {
                        switch (dato) {
                            case "EstadoRegistro" -> registro.estado = estado(lector.getElementText());
                            case "CodigoErrorRegistro" ->
                                registro.codigoError =
                                        Integer.valueOf(lector.getElementText().trim());
                            case "DescripcionErrorRegistro" -> registro.descripcionError = lector.getElementText();
                            default -> LectorRespuestaAeat.saltar(lector);
                        }
                    });
                default -> LectorRespuestaAeat.saltar(lector);
            }
        });
        if (registro.idFactura == null || registro.estado == null) {
            throw new RespuestaIlegibleException("un registro de la consulta no trae IDFactura o EstadoRegistro");
        }
        return new RegistroEnAeat(
                registro.idFactura,
                registro.huella,
                registro.estado,
                registro.codigoError,
                registro.descripcionError,
                registro.presentado);
    }

    private static IdFactura idFactura(XMLStreamReader lector) throws XMLStreamException {
        String[] campos = new String[3];
        hijos(lector, hijo -> {
            switch (hijo) {
                case "IDEmisorFactura" -> campos[0] = lector.getElementText().trim();
                case "NumSerieFactura" -> campos[1] = lector.getElementText();
                case "FechaExpedicionFactura" ->
                    campos[2] = lector.getElementText().trim();
                default -> LectorRespuestaAeat.saltar(lector);
            }
        });
        if (campos[0] == null || campos[1] == null || campos[2] == null) {
            throw new RespuestaIlegibleException("una identificación de factura de la consulta está incompleta");
        }
        return new IdFactura(new Nif(campos[0]), campos[1], LocalDate.parse(campos[2], FECHA));
    }

    /**
     * El XSD declara {@code Correcto}, {@code AceptadoConErrores} y {@code Anulado}; su propia
     * documentación y el ejemplo del servicio usan el femenino.
     */
    private static RegistroEnAeat.Estado estado(String valor) {
        return switch (valor.trim()) {
            case "Correcto", "Correcta" -> RegistroEnAeat.Estado.CORRECTO;
            case "AceptadoConErrores", "AceptadaConErrores" -> RegistroEnAeat.Estado.ACEPTADO_CON_ERRORES;
            case "Anulado", "Anulada" -> RegistroEnAeat.Estado.ANULADO;
            default -> throw new RespuestaIlegibleException("estado de registro desconocido: " + valor);
        };
    }

    /** Recorre los hijos directos del elemento en curso; cada visita consume su hijo entero. */
    private static void hijos(XMLStreamReader lector, Visita visita) throws XMLStreamException {
        while (true) {
            int evento = lector.next();
            if (evento == XMLStreamConstants.START_ELEMENT) {
                visita.visitar(lector.getLocalName());
            } else if (evento == XMLStreamConstants.END_ELEMENT) {
                return;
            }
        }
    }

    @FunctionalInterface
    private interface Visita {
        void visitar(String elemento) throws XMLStreamException;
    }

    /** Acumulador de un registro a medio leer. */
    private static final class Registro {
        private IdFactura idFactura;
        private Huella huella;
        private RegistroEnAeat.Estado estado;
        private Integer codigoError;
        private String descripcionError;
        private OffsetDateTime presentado;
    }
}
