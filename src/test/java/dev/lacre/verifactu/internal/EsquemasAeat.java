package dev.lacre.verifactu.internal;

import org.w3c.dom.ls.LSInput;
import org.w3c.dom.ls.LSResourceResolver;
import org.xml.sax.SAXException;

import javax.xml.XMLConstants;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import java.io.InputStream;
import java.io.Reader;
import java.net.URL;
import java.util.Map;

/**
 * Compila los esquemas oficiales de la AEAT resolviendo <strong>todo</strong> desde el
 * classpath.
 * <p>
 * Hace falta porque {@code SuministroInformacion.xsd} importa el esquema de firma XML con una
 * URL absoluta a la W3C. Tal cual, cada validación se iría a la red y el CI dependería de que
 * {@code w3.org} responda.
 * <p>
 * Se vendorizan tres ficheros del W3C: el esquema de firma y las dos DTD que arrastra su
 * {@code DOCTYPE} ({@code XMLSchema.dtd} y, a través de ella, {@code datatypes.dtd}). Servir
 * una DTD vacía no vale: Xerces la ignora, sale a la red igualmente y el documento acaba sin
 * parsear.
 * <p>
 * El resolutor <strong>lanza</strong> ante cualquier recurso que no tenga en local, en lugar de
 * devolver {@code null}. Devolver {@code null} le diría al parser que resuelva por su cuenta,
 * es decir, que salga a la red en silencio: exactamente lo que se quiere impedir. Que estos
 * tests pasen demuestra que no hay ni una petición saliente.
 */
public final class EsquemasAeat {

    /** Clave: el fichero pedido, sea por URL absoluta o por nombre relativo. */
    private static final Map<String, String> EN_CLASSPATH = Map.of(
            "SuministroInformacion.xsd", "/xsd/aeat/SuministroInformacion.xsd",
            "SuministroLR.xsd", "/xsd/aeat/SuministroLR.xsd",
            "RespuestaSuministro.xsd", "/xsd/aeat/RespuestaSuministro.xsd",
            "ConsultaLR.xsd", "/xsd/aeat/ConsultaLR.xsd",
            "RespuestaConsultaLR.xsd", "/xsd/aeat/RespuestaConsultaLR.xsd",
            "EventosSIF.xsd", "/xsd/aeat/EventosSIF.xsd",
            "xmldsig-core-schema.xsd", "/xsd/w3c/xmldsig-core-schema.xsd",
            "XMLSchema.dtd", "/xsd/w3c/XMLSchema.dtd",
            "datatypes.dtd", "/xsd/w3c/datatypes.dtd");

    private static final String LIMITE_OCURRENCIAS = "jdk.xml.maxOccurLimit";

    private EsquemasAeat() {
    }

    /** Esquema de alta y anulación, el que valida los registros que remitimos. */
    public static Schema suministroLr() {
        return compilar("SuministroLR.xsd");
    }

    public static Schema compilar(String fichero) {
        SchemaFactory factory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
        factory.setResourceResolver(new ResolutorLocal());
        try {
            // RespuestaConsultaLR.xsd declara maxOccurs="10000" y la JDK corta en 5.000 para
            // protegerse de esquemas hostiles. Aquí solo se compilan los XSD oficiales, ya
            // vendorizados en el classpath, así que subir el límite no abre ninguna puerta.
            factory.setProperty(LIMITE_OCURRENCIAS, "20000");
        } catch (SAXException e) {
            throw new IllegalStateException(
                    "Esta JVM no admite la propiedad " + LIMITE_OCURRENCIAS, e);
        }
        try {
            // Sin ACCESS_EXTERNAL_SCHEMA: esa restricción se aplica antes de consultar al
            // resolutor y hace que el import del xmldsig se descarte en silencio, con lo que
            // ds:Signature queda sin declarar. Quien garantiza que no hay red es el resolutor,
            // que lanza ante cualquier recurso que no tenga en local.
            return factory.newSchema(new StreamSource(recurso(fichero).toExternalForm()));
        } catch (SAXException e) {
            throw new IllegalStateException("No se pudo compilar el esquema " + fichero, e);
        }
    }

    private static URL recurso(String fichero) {
        String ruta = EN_CLASSPATH.get(fichero);
        if (ruta == null) {
            throw new IllegalStateException("No hay copia local del esquema " + fichero);
        }
        URL url = EsquemasAeat.class.getResource(ruta);
        if (url == null) {
            throw new IllegalStateException("Falta el recurso " + ruta + " en el classpath");
        }
        return url;
    }

    private static final class ResolutorLocal implements LSResourceResolver {

        @Override
        public LSInput resolveResource(String type, String namespaceURI, String publicId,
                                       String systemId, String baseURI) {
            if (systemId == null) {
                throw new IllegalStateException(
                        "El parser pidió un recurso sin systemId (namespace " + namespaceURI + ")");
            }
            String fichero = systemId.substring(systemId.lastIndexOf('/') + 1);
            return new EntradaLocal(recurso(fichero), systemId, publicId);
        }
    }

    /** Implementación mínima de {@link LSInput}: solo se usan el flujo y los identificadores. */
    private record EntradaLocal(URL url, String systemId, String publicId) implements LSInput {

        @Override
        public InputStream getByteStream() {
            try {
                return url.openStream();
            } catch (java.io.IOException e) {
                throw new IllegalStateException("No se pudo leer " + url, e);
            }
        }

        @Override
        public String getSystemId() {
            return systemId;
        }

        @Override
        public String getPublicId() {
            return publicId;
        }

        @Override
        public String getBaseURI() {
            return url.toExternalForm();
        }

        @Override
        public Reader getCharacterStream() {
            return null;
        }

        @Override
        public String getStringData() {
            return null;
        }

        @Override
        public String getEncoding() {
            return null;
        }

        @Override
        public boolean getCertifiedText() {
            return false;
        }

        @Override
        public void setCharacterStream(Reader characterStream) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void setByteStream(InputStream byteStream) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void setStringData(String stringData) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void setSystemId(String systemId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void setPublicId(String publicId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void setBaseURI(String baseURI) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void setEncoding(String encoding) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void setCertifiedText(boolean certifiedText) {
            throw new UnsupportedOperationException();
        }
    }
}
