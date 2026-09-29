package dev.lacre.verifactu.internal.xml;

import dev.lacre.shared.IdOtro;
import dev.lacre.shared.IdentificadorFiscal;
import dev.lacre.shared.Importe;
import dev.lacre.shared.Nif;
import dev.lacre.shared.Porcentaje;
import dev.lacre.verifactu.desglose.DetalleDesglose;
import dev.lacre.verifactu.desglose.OperacionExenta;
import dev.lacre.verifactu.internal.FormatosAeat;
import dev.lacre.verifactu.registro.DatosRegistroAlta;
import dev.lacre.verifactu.registro.DatosRegistroAnulacion;
import dev.lacre.verifactu.registro.IdFactura;
import dev.lacre.verifactu.registro.PersonaFisicaJuridica;
import dev.lacre.verifactu.registro.RegistroAnterior;
import dev.lacre.verifactu.registro.RegistroEncadenado;
import dev.lacre.verifactu.registro.SistemaInformatico;
import java.io.StringWriter;
import java.util.List;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;

/**
 * Serializa un registro al XML de {@code sf:RegistroAlta} o {@code sf:RegistroAnulacion}, en el
 * orden de la secuencia del XSD. Produce un fragmento sin declaración XML, con el espacio de
 * nombres en el elemento raíz. Los indicadores opcionales solo se emiten cuando valen {@code S}.
 */
public final class EscritorRegistro {

    public static final String NS = "https://www2.agenciatributaria.gob.es/static_files/common/"
            + "internet/dep/aplicaciones/es/aeat/tike/cont/ws/SuministroInformacion.xsd";

    private static final String PREFIJO = "sf";

    private final XMLStreamWriter xml;

    private EscritorRegistro(XMLStreamWriter xml) {
        this.xml = xml;
    }

    public static String escribir(RegistroEncadenado registro) {
        StringWriter salida = new StringWriter();
        try {
            XMLStreamWriter xml = XMLOutputFactory.newInstance().createXMLStreamWriter(salida);
            new EscritorRegistro(xml).registro(registro);
            xml.flush();
        } catch (XMLStreamException e) {
            throw new IllegalStateException("No se pudo serializar el registro de alta", e);
        }
        return salida.toString();
    }

    private void registro(RegistroEncadenado registro) throws XMLStreamException {
        xml.setPrefix(PREFIJO, NS);
        switch (registro.datos()) {
            case DatosRegistroAlta alta -> registroAlta(registro, alta);
            case DatosRegistroAnulacion anulacion -> registroAnulacion(registro, anulacion);
        }
    }

    private void registroAlta(RegistroEncadenado registro, DatosRegistroAlta datos) throws XMLStreamException {
        xml.writeStartElement(NS, "RegistroAlta");
        xml.writeNamespace(PREFIJO, NS);

        texto("IDVersion", DatosRegistroAlta.ID_VERSION);
        idFactura("IDFactura", datos.idFactura());
        opcional("RefExterna", datos.refExterna());
        texto("NombreRazonEmisor", datos.nombreRazonEmisor());
        indicadorSiVerdadero("Subsanacion", datos.subsanacion());
        if (datos.rechazoPrevio() != null) {
            texto("RechazoPrevio", datos.rechazoPrevio().codigo());
        }
        texto("TipoFactura", datos.tipoFactura().codigo());
        if (datos.tipoRectificativa() != null) {
            texto("TipoRectificativa", datos.tipoRectificativa().codigo());
        }
        facturasReferenciadas("FacturasRectificadas", "IDFacturaRectificada", datos.facturasRectificadas());
        facturasReferenciadas("FacturasSustituidas", "IDFacturaSustituida", datos.facturasSustituidas());
        if (datos.importeRectificacion() != null) {
            xml.writeStartElement(NS, "ImporteRectificacion");
            importe("BaseRectificada", datos.importeRectificacion().baseRectificada());
            importe("CuotaRectificada", datos.importeRectificacion().cuotaRectificada());
            importeOpcional(
                    "CuotaRecargoRectificado", datos.importeRectificacion().cuotaRecargoRectificado());
            xml.writeEndElement();
        }
        if (datos.fechaOperacion() != null) {
            texto("FechaOperacion", FormatosAeat.fecha(datos.fechaOperacion()));
        }
        texto("DescripcionOperacion", datos.descripcionOperacion());
        indicadorSiVerdadero("FacturaSimplificadaArt7273", datos.facturaSimplificadaArt7273());
        indicadorSiVerdadero("FacturaSinIdentifDestinatarioArt61d", datos.facturaSinIdentifDestinatarioArt61d());
        indicadorSiVerdadero("Macrodato", datos.macrodato());
        if (datos.emitidaPorTerceroODestinatario() != null) {
            texto(
                    "EmitidaPorTerceroODestinatario",
                    datos.emitidaPorTerceroODestinatario().codigo());
        }
        if (datos.tercero() != null) {
            persona("Tercero", datos.tercero());
        }
        if (!datos.destinatarios().isEmpty()) {
            xml.writeStartElement(NS, "Destinatarios");
            for (PersonaFisicaJuridica destinatario : datos.destinatarios()) {
                persona("IDDestinatario", destinatario);
            }
            xml.writeEndElement();
        }
        indicadorSiVerdadero("Cupon", datos.cupon());
        desglose(datos);
        importe("CuotaTotal", datos.cuotaTotal());
        importe("ImporteTotal", datos.importeTotal());
        encadenamiento(registro.registroAnterior());
        sistemaInformatico(datos.sistemaInformatico());
        texto("FechaHoraHusoGenRegistro", FormatosAeat.fechaHoraHuso(registro.fechaHoraHusoGenRegistro()));
        opcional("NumRegistroAcuerdoFacturacion", datos.numRegistroAcuerdoFacturacion());
        opcional("IdAcuerdoSistemaInformatico", datos.idAcuerdoSistemaInformatico());
        texto("TipoHuella", DatosRegistroAlta.TIPO_HUELLA);
        texto("Huella", registro.huella().valor());

        xml.writeEndElement();
    }

    /** Sin desglose ni importes; los elementos de identificación llevan el sufijo {@code Anulada}. */
    private void registroAnulacion(RegistroEncadenado registro, DatosRegistroAnulacion datos)
            throws XMLStreamException {
        xml.writeStartElement(NS, "RegistroAnulacion");
        xml.writeNamespace(PREFIJO, NS);

        texto("IDVersion", DatosRegistroAlta.ID_VERSION);
        xml.writeStartElement(NS, "IDFactura");
        texto("IDEmisorFacturaAnulada", datos.idFactura().emisor().valor());
        texto("NumSerieFacturaAnulada", datos.idFactura().numSerieFactura());
        texto(
                "FechaExpedicionFacturaAnulada",
                FormatosAeat.fecha(datos.idFactura().fechaExpedicion()));
        xml.writeEndElement();
        opcional("RefExterna", datos.refExterna());
        indicadorSiVerdadero("SinRegistroPrevio", datos.sinRegistroPrevio());
        indicadorSiVerdadero("RechazoPrevio", datos.rechazoPrevio());
        if (datos.generadoPor() != null) {
            texto("GeneradoPor", datos.generadoPor().codigo());
        }
        if (datos.generador() != null) {
            persona("Generador", datos.generador());
        }
        encadenamiento(registro.registroAnterior());
        sistemaInformatico(datos.sistemaInformatico());
        texto("FechaHoraHusoGenRegistro", FormatosAeat.fechaHoraHuso(registro.fechaHoraHusoGenRegistro()));
        texto("TipoHuella", DatosRegistroAlta.TIPO_HUELLA);
        texto("Huella", registro.huella().valor());

        xml.writeEndElement();
    }

    private void desglose(DatosRegistroAlta datos) throws XMLStreamException {
        xml.writeStartElement(NS, "Desglose");
        for (DetalleDesglose detalle : datos.desglose().detalles()) {
            xml.writeStartElement(NS, "DetalleDesglose");
            if (detalle.impuesto() != null) {
                texto("Impuesto", detalle.impuesto().codigo());
            }
            if (detalle.claveRegimen() != null) {
                texto("ClaveRegimen", detalle.claveRegimen().codigo());
            }
            if (detalle.calificacion() instanceof OperacionExenta exenta) {
                texto("OperacionExenta", exenta.codigo());
            } else {
                texto("CalificacionOperacion", detalle.calificacion().codigo());
            }
            porcentajeOpcional("TipoImpositivo", detalle.tipoImpositivo());
            importe("BaseImponibleOimporteNoSujeto", detalle.baseImponibleOimporteNoSujeto());
            importeOpcional("BaseImponibleACoste", detalle.baseImponibleACoste());
            importeOpcional("CuotaRepercutida", detalle.cuotaRepercutida());
            porcentajeOpcional("TipoRecargoEquivalencia", detalle.tipoRecargoEquivalencia());
            importeOpcional("CuotaRecargoEquivalencia", detalle.cuotaRecargoEquivalencia());
            xml.writeEndElement();
        }
        xml.writeEndElement();
    }

    private void encadenamiento(java.util.Optional<RegistroAnterior> anterior) throws XMLStreamException {
        xml.writeStartElement(NS, "Encadenamiento");
        if (anterior.isEmpty()) {
            texto("PrimerRegistro", "S");
        } else {
            RegistroAnterior enlace = anterior.get();
            xml.writeStartElement(NS, "RegistroAnterior");
            texto("IDEmisorFactura", enlace.idFactura().emisor().valor());
            texto("NumSerieFactura", enlace.idFactura().numSerieFactura());
            texto(
                    "FechaExpedicionFactura",
                    FormatosAeat.fecha(enlace.idFactura().fechaExpedicion()));
            texto("Huella", enlace.huella().valor());
            xml.writeEndElement();
        }
        xml.writeEndElement();
    }

    private void sistemaInformatico(SistemaInformatico sistema) throws XMLStreamException {
        xml.writeStartElement(NS, "SistemaInformatico");
        texto("NombreRazon", sistema.productor().nombreRazon());
        identificador(sistema.productor().identificador());
        texto("NombreSistemaInformatico", sistema.nombreSistemaInformatico());
        texto("IdSistemaInformatico", sistema.idSistemaInformatico());
        texto("Version", sistema.version());
        texto("NumeroInstalacion", sistema.numeroInstalacion());
        texto("TipoUsoPosibleSoloVerifactu", FormatosAeat.indicador(sistema.tipoUsoPosibleSoloVerifactu()));
        texto("TipoUsoPosibleMultiOT", FormatosAeat.indicador(sistema.tipoUsoPosibleMultiOT()));
        texto("IndicadorMultiplesOT", FormatosAeat.indicador(sistema.indicadorMultiplesOT()));
        xml.writeEndElement();
    }

    private void facturasReferenciadas(String grupo, String elemento, List<IdFactura> facturas)
            throws XMLStreamException {
        if (facturas.isEmpty()) {
            return;
        }
        xml.writeStartElement(NS, grupo);
        for (IdFactura factura : facturas) {
            idFactura(elemento, factura);
        }
        xml.writeEndElement();
    }

    private void idFactura(String elemento, IdFactura factura) throws XMLStreamException {
        xml.writeStartElement(NS, elemento);
        texto("IDEmisorFactura", factura.emisor().valor());
        texto("NumSerieFactura", factura.numSerieFactura());
        texto("FechaExpedicionFactura", FormatosAeat.fecha(factura.fechaExpedicion()));
        xml.writeEndElement();
    }

    private void persona(String elemento, PersonaFisicaJuridica persona) throws XMLStreamException {
        xml.writeStartElement(NS, elemento);
        texto("NombreRazon", persona.nombreRazon());
        identificador(persona.identificador());
        xml.writeEndElement();
    }

    private void identificador(IdentificadorFiscal identificador) throws XMLStreamException {
        switch (identificador) {
            case Nif nif -> texto("NIF", nif.valor());
            case IdOtro otro -> {
                xml.writeStartElement(NS, "IDOtro");
                opcional("CodigoPais", otro.codigoPais());
                texto("IDType", otro.tipo().codigo());
                texto("ID", otro.id());
                xml.writeEndElement();
            }
        }
    }

    private void texto(String elemento, String valor) throws XMLStreamException {
        xml.writeStartElement(NS, elemento);
        xml.writeCharacters(valor);
        xml.writeEndElement();
    }

    private void opcional(String elemento, String valor) throws XMLStreamException {
        if (valor != null) {
            texto(elemento, valor);
        }
    }

    private void indicadorSiVerdadero(String elemento, boolean valor) throws XMLStreamException {
        if (valor) {
            texto(elemento, FormatosAeat.indicador(true));
        }
    }

    private void importe(String elemento, Importe valor) throws XMLStreamException {
        texto(elemento, FormatosAeat.importe(valor));
    }

    private void importeOpcional(String elemento, Importe valor) throws XMLStreamException {
        if (valor != null) {
            importe(elemento, valor);
        }
    }

    private void porcentajeOpcional(String elemento, Porcentaje valor) throws XMLStreamException {
        if (valor != null) {
            texto(elemento, valor.valor().toPlainString());
        }
    }
}
