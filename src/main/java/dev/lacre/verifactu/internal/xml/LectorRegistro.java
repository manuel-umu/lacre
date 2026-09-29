package dev.lacre.verifactu.internal.xml;

import dev.lacre.shared.Huella;
import dev.lacre.shared.Importe;
import dev.lacre.shared.Nif;
import dev.lacre.shared.ValorInvalidoException;
import dev.lacre.verifactu.internal.FormatosAeat;
import dev.lacre.verifactu.registro.CamposDeHuella;
import dev.lacre.verifactu.registro.IdFactura;
import dev.lacre.verifactu.registro.RegistroAnterior;
import dev.lacre.verifactu.registro.TipoFactura;
import java.io.StringReader;
import java.time.DateTimeException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

/**
 * Lee de un fragmento {@code RegistroAlta} o {@code RegistroAnulacion} lo que hace falta para
 * recalcular su huella. Lee por ruta y no por nombre de elemento: en un alta rectificativa,
 * {@code NumSerieFactura} aparece tres veces y {@code Huella}, dos.
 */
public final class LectorRegistro {

    private static final String ANTERIOR = "Encadenamiento/RegistroAnterior/";

    /** Rutas relativas al elemento raíz de los valores que se leen. */
    private static final Set<String> HOJAS = Set.of(
            "IDFactura/IDEmisorFactura",
            "IDFactura/NumSerieFactura",
            "IDFactura/FechaExpedicionFactura",
            "IDFactura/IDEmisorFacturaAnulada",
            "IDFactura/NumSerieFacturaAnulada",
            "IDFactura/FechaExpedicionFacturaAnulada",
            "TipoFactura",
            "CuotaTotal",
            "ImporteTotal",
            ANTERIOR + "IDEmisorFactura",
            ANTERIOR + "NumSerieFactura",
            ANTERIOR + "FechaExpedicionFactura",
            ANTERIOR + "Huella",
            "FechaHoraHusoGenRegistro",
            "Huella");

    private LectorRegistro() {}

    /** @throws RegistroIlegibleException si no es XML, no es un registro o le falta un campo */
    public static RegistroLeido leer(String xml) {
        try {
            XMLStreamReader lector = entradaSegura().createXMLStreamReader(new StringReader(xml));
            try {
                return leer(lector);
            } finally {
                lector.close();
            }
        } catch (XMLStreamException | DateTimeException | IllegalArgumentException | ValorInvalidoException e) {
            throw new RegistroIlegibleException(e.getMessage(), e);
        }
    }

    private static RegistroLeido leer(XMLStreamReader lector) throws XMLStreamException {
        String raiz = null;
        List<String> ruta = new ArrayList<>();
        Map<String, String> valores = new HashMap<>();

        while (lector.hasNext()) {
            int evento = lector.next();
            if (evento == XMLStreamConstants.START_ELEMENT) {
                if (raiz == null) {
                    raiz = lector.getLocalName();
                    continue;
                }
                ruta.add(lector.getLocalName());
                String camino = String.join("/", ruta);
                if (HOJAS.contains(camino)) {
                    valores.put(camino, lector.getElementText());
                    ruta.removeLast();
                }
            } else if (evento == XMLStreamConstants.END_ELEMENT && !ruta.isEmpty()) {
                ruta.removeLast();
            }
        }

        CamposDeHuella campos = switch (raiz) {
            case "RegistroAlta" ->
                new CamposDeHuella.Alta(
                        idFactura(valores, "IDFactura/", ""),
                        TipoFactura.valueOf(obligatorio(valores, "TipoFactura")),
                        Importe.de(obligatorio(valores, "CuotaTotal")),
                        Importe.de(obligatorio(valores, "ImporteTotal")));
            case "RegistroAnulacion" -> new CamposDeHuella.Anulacion(idFactura(valores, "IDFactura/", "Anulada"));
            case null, default ->
                throw new RegistroIlegibleException("no es un registro de alta ni de anulación, sino " + raiz);
        };

        Optional<RegistroAnterior> anterior = valores.containsKey(ANTERIOR + "Huella")
                ? Optional.of(new RegistroAnterior(
                        idFactura(valores, ANTERIOR, ""), new Huella(valores.get(ANTERIOR + "Huella"))))
                : Optional.empty();

        OffsetDateTime fechaHora = FormatosAeat.leerFechaHoraHuso(obligatorio(valores, "FechaHoraHusoGenRegistro"));

        return new RegistroLeido(campos, anterior, fechaHora, new Huella(obligatorio(valores, "Huella")));
    }

    /** Los campos de la anulación llevan el sufijo {@code Anulada}; los del anterior, no. */
    private static IdFactura idFactura(Map<String, String> valores, String prefijo, String sufijo) {
        return new IdFactura(
                new Nif(obligatorio(valores, prefijo + "IDEmisorFactura" + sufijo)),
                obligatorio(valores, prefijo + "NumSerieFactura" + sufijo),
                FormatosAeat.leerFecha(obligatorio(valores, prefijo + "FechaExpedicionFactura" + sufijo)));
    }

    private static String obligatorio(Map<String, String> valores, String camino) {
        String valor = valores.get(camino);
        if (valor == null) {
            throw new RegistroIlegibleException("falta " + camino);
        }
        return valor;
    }

    /** Sin DTD ni entidades externas: la defensa estándar contra XXE. */
    private static XMLInputFactory entradaSegura() {
        XMLInputFactory factoria = XMLInputFactory.newDefaultFactory();
        factoria.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
        factoria.setProperty(XMLInputFactory.SUPPORT_DTD, false);
        return factoria;
    }
}
