package dev.lacre.remision.internal.adaptador;

import dev.lacre.identidad.ObligadoTributario;

import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;
import java.io.StringWriter;
import java.util.List;

/**
 * Envuelve los registros ya serializados en el mensaje SOAP que espera la AEAT.
 * <p>
 * El <a href="../../../../../../docs/adr/0003-serializacion-xml-con-stax.md">ADR 0003</a> dejó
 * dicho que {@code EscritorRegistro} serializa <em>un registro</em> y que la envoltura
 * {@code RegFactuSistemaFacturacion} la construye este módulo, porque es quien sabe agrupar
 * hasta 1000 registros por envío.
 * <p>
 * <strong>Los fragmentos se insertan tal cual.</strong> Cada uno declara su propio espacio de
 * nombres —por eso el ADR 0003 los hizo autónomos— y su contenido ya salió escapado de StAX, así
 * que concatenar solo une marcado estático. No es «montar XML con plantillas»: el único valor
 * variable de este fichero es la cabecera, y esa sí se escribe con StAX. Quien lo demuestra es la
 * validación del cuerpo contra el XSD oficial, que corre en el test.
 */
final class EscritorLote {

    static final String NS_SOAP = "http://schemas.xmlsoap.org/soap/envelope/";
    static final String NS_LR = "https://www2.agenciatributaria.gob.es/static_files/common/"
            + "internet/dep/aplicaciones/es/aeat/tike/cont/ws/SuministroLR.xsd";
    static final String NS_SF = "https://www2.agenciatributaria.gob.es/static_files/common/"
            + "internet/dep/aplicaciones/es/aeat/tike/cont/ws/SuministroInformacion.xsd";

    /** Tope que impone la AEAT por envío. */
    static final int MAXIMO_REGISTROS_POR_ENVIO = 1000;

    private EscritorLote() {
    }

    static String envolver(ObligadoTributario obligado, List<String> registros) {
        if (registros.isEmpty()) {
            throw new IllegalArgumentException("Un envío sin registros no tiene nada que remitir");
        }
        if (registros.size() > MAXIMO_REGISTROS_POR_ENVIO) {
            throw new IllegalArgumentException("La AEAT admite " + MAXIMO_REGISTROS_POR_ENVIO
                    + " registros por envío y se han pasado " + registros.size());
        }

        StringBuilder mensaje = new StringBuilder(1024 + registros.size() * 4096);
        mensaje.append("<soapenv:Envelope xmlns:soapenv=\"").append(NS_SOAP)
                .append("\" xmlns:sfLR=\"").append(NS_LR).append("\">")
                .append("<soapenv:Header/><soapenv:Body>")
                .append("<sfLR:RegFactuSistemaFacturacion>")
                .append(cabecera(obligado));
        for (String registro : registros) {
            mensaje.append("<sfLR:RegistroFactura>").append(registro).append("</sfLR:RegistroFactura>");
        }
        return mensaje.append("</sfLR:RegFactuSistemaFacturacion>")
                .append("</soapenv:Body></soapenv:Envelope>")
                .toString();
    }

    /**
     * El único trozo con datos variables, y por eso el único que pasa por StAX: un nombre o razón
     * social con un {@code &} rompería el documento si se concatenase a mano.
     * <p>
     * Sin {@code RemisionVoluntaria}: sus dos hijos son opcionales y solo aplican a casos
     * concretos —cese de Veri*Factu a fin de año e incidencia—, así que el documento mínimo la
     * omite, igual que hace el ejemplo oficial del documento de descripción del servicio.
     */
    private static String cabecera(ObligadoTributario obligado) {
        StringWriter destino = new StringWriter();
        try {
            XMLStreamWriter xml = XMLOutputFactory.newInstance().createXMLStreamWriter(destino);
            xml.setPrefix("sfLR", NS_LR);
            xml.setPrefix("sf", NS_SF);

            xml.writeStartElement(NS_LR, "Cabecera");
            xml.writeNamespace("sfLR", NS_LR);
            xml.writeNamespace("sf", NS_SF);

            xml.writeStartElement(NS_SF, "ObligadoEmision");
            escribir(xml, "NombreRazon", obligado.nombreRazon());
            escribir(xml, "NIF", obligado.nif().valor());
            xml.writeEndElement();

            xml.writeEndElement();
            xml.writeEndDocument();
            xml.flush();
        } catch (XMLStreamException e) {
            throw new IllegalStateException("No se pudo escribir la cabecera del envío", e);
        }
        return destino.toString();
    }

    private static void escribir(XMLStreamWriter xml, String elemento, String valor)
            throws XMLStreamException {
        xml.writeStartElement(NS_SF, elemento);
        xml.writeCharacters(valor);
        xml.writeEndElement();
    }
}
