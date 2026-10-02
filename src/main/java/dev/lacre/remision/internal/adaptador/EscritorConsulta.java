package dev.lacre.remision.internal.adaptador;

import dev.lacre.identidad.ObligadoTributario;
import dev.lacre.verifactu.registro.IdFactura;
import java.io.StringWriter;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;

/** Escribe con StAX el mensaje SOAP {@code ConsultaFactuSistemaFacturacion} del obligado como emisor. */
final class EscritorConsulta {

    static final String NS_CONSULTA = "https://www2.agenciatributaria.gob.es/static_files/common/"
            + "internet/dep/aplicaciones/es/aeat/tike/cont/ws/ConsultaLR.xsd";

    /** {@code sf:fecha}: {@code dd-MM-yyyy}. */
    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd-MM-yyyy");

    private EscritorConsulta() {}

    /** @param clavePaginacion el último registro de la página anterior, o nulo en la primera */
    static String envolver(ObligadoTributario obligado, YearMonth periodo, IdFactura clavePaginacion) {
        StringWriter destino = new StringWriter();
        try {
            XMLStreamWriter xml = XMLOutputFactory.newInstance().createXMLStreamWriter(destino);
            xml.setPrefix("soapenv", EscritorLote.NS_SOAP);
            xml.setPrefix("con", NS_CONSULTA);
            xml.setPrefix("sf", EscritorLote.NS_SF);

            xml.writeStartElement(EscritorLote.NS_SOAP, "Envelope");
            xml.writeNamespace("soapenv", EscritorLote.NS_SOAP);
            xml.writeNamespace("con", NS_CONSULTA);
            xml.writeNamespace("sf", EscritorLote.NS_SF);
            xml.writeEmptyElement(EscritorLote.NS_SOAP, "Header");
            xml.writeStartElement(EscritorLote.NS_SOAP, "Body");
            xml.writeStartElement(NS_CONSULTA, "ConsultaFactuSistemaFacturacion");

            xml.writeStartElement(NS_CONSULTA, "Cabecera");
            escribir(xml, "IDVersion", "1.0");
            xml.writeStartElement(EscritorLote.NS_SF, "ObligadoEmision");
            escribir(xml, "NombreRazon", obligado.nombreRazon());
            escribir(xml, "NIF", obligado.nif().valor());
            xml.writeEndElement();
            xml.writeEndElement();

            xml.writeStartElement(NS_CONSULTA, "FiltroConsulta");
            xml.writeStartElement(NS_CONSULTA, "PeriodoImputacion");
            escribir(xml, "Ejercicio", String.valueOf(periodo.getYear()));
            escribir(xml, "Periodo", "%02d".formatted(periodo.getMonthValue()));
            xml.writeEndElement();
            if (clavePaginacion != null) {
                xml.writeStartElement(NS_CONSULTA, "ClavePaginacion");
                escribir(xml, "IDEmisorFactura", clavePaginacion.emisor().valor());
                escribir(xml, "NumSerieFactura", clavePaginacion.numSerieFactura());
                escribir(
                        xml,
                        "FechaExpedicionFactura",
                        clavePaginacion.fechaExpedicion().format(FECHA));
                xml.writeEndElement();
            }
            xml.writeEndElement();

            xml.writeEndElement();
            xml.writeEndElement();
            xml.writeEndElement();
            xml.writeEndDocument();
            xml.flush();
        } catch (XMLStreamException e) {
            throw new IllegalStateException("No se pudo escribir la consulta", e);
        }
        return destino.toString();
    }

    private static void escribir(XMLStreamWriter xml, String elemento, String valor) throws XMLStreamException {
        xml.writeStartElement(EscritorLote.NS_SF, elemento);
        xml.writeCharacters(valor);
        xml.writeEndElement();
    }
}
