package dev.lacre.verifactu.registro;

import dev.lacre.shared.Importe;
import dev.lacre.shared.ReglaAeatIncumplidaException;
import dev.lacre.shared.Textos;
import dev.lacre.shared.ValorInvalidoException;
import dev.lacre.verifactu.desglose.ClaveRegimen;
import dev.lacre.verifactu.desglose.Desglose;
import dev.lacre.verifactu.desglose.DetalleDesglose;
import dev.lacre.verifactu.huella.Canonicalizador;
import dev.lacre.verifactu.huella.EncadenadorRegistros;

import java.time.LocalDate;
import java.util.List;

/**
 * Contenido de un registro de facturación de alta, {@code RegistroFacturacionAltaType} del XSD.
 * No incluye el encadenamiento, la fecha de generación ni la huella, que produce
 * {@link EncadenadorRegistros}. El orden de los componentes no tiene significado normativo: el
 * de la huella lo fija {@link Canonicalizador}. Se construye con {@link #builder()}.
 */
public record DatosRegistroAlta(
        IdFactura idFactura,
        String refExterna,
        String nombreRazonEmisor,
        boolean subsanacion,
        RechazoPrevio rechazoPrevio,
        TipoFactura tipoFactura,
        ClaveTipoRectificativa tipoRectificativa,
        List<IdFactura> facturasRectificadas,
        List<IdFactura> facturasSustituidas,
        ImporteRectificacion importeRectificacion,
        LocalDate fechaOperacion,
        String descripcionOperacion,
        boolean facturaSimplificadaArt7273,
        boolean facturaSinIdentifDestinatarioArt61d,
        boolean macrodato,
        EmitidaPor emitidaPorTerceroODestinatario,
        PersonaFisicaJuridica tercero,
        List<PersonaFisicaJuridica> destinatarios,
        boolean cupon,
        Desglose desglose,
        Importe cuotaTotal,
        Importe importeTotal,
        SistemaInformatico sistemaInformatico,
        String numRegistroAcuerdoFacturacion,
        String idAcuerdoSistemaInformatico) implements DatosRegistro {

    @Override
    public CamposDeHuella camposDeHuella() {
        return new CamposDeHuella.Alta(idFactura, tipoFactura, cuotaTotal, importeTotal);
    }

    @Override
    public TipoRegistro tipo() {
        return TipoRegistro.ALTA;
    }

    /** Única versión admitida por {@code VersionType}. */
    public static final String ID_VERSION = "1.0";

    /** Único algoritmo admitido por {@code TipoHuellaType}: SHA-256. */
    public static final String TIPO_HUELLA = "01";

    public static final int MAXIMO_LONGITUD_REF_EXTERNA = 60;
    public static final int MAXIMO_LONGITUD_NOMBRE_EMISOR = 120;
    public static final int MAXIMO_LONGITUD_DESCRIPCION = 500;
    public static final int MAXIMO_FACTURAS_REFERENCIADAS = 1000;
    public static final int MAXIMO_DESTINATARIOS = 1000;
    public static final int MAXIMO_LONGITUD_NUM_ACUERDO = 15;
    public static final int MAXIMO_LONGITUD_ID_ACUERDO = 16;

    /** Margen que admite la AEAT al contrastar los totales con el desglose. */
    public static final Importe MARGEN_CUADRE = Importe.de("10.00");

    public DatosRegistroAlta {
        if (idFactura == null) {
            throw new ValorInvalidoException("La identificación de la factura es obligatoria");
        }
        refExterna = Textos.opcional(refExterna, MAXIMO_LONGITUD_REF_EXTERNA, "La referencia externa");
        nombreRazonEmisor = Textos.obligatorio(
                nombreRazonEmisor, MAXIMO_LONGITUD_NOMBRE_EMISOR, "El nombre o razón social del emisor");
        if (tipoFactura == null) {
            throw new ValorInvalidoException("El tipo de factura es obligatorio");
        }
        descripcionOperacion = Textos.obligatorio(
                descripcionOperacion, MAXIMO_LONGITUD_DESCRIPCION, "La descripción de la operación");
        if (desglose == null) {
            throw new ValorInvalidoException("El desglose es obligatorio");
        }
        if (cuotaTotal == null) {
            throw new ValorInvalidoException("La cuota total es obligatoria");
        }
        if (importeTotal == null) {
            throw new ValorInvalidoException("El importe total es obligatorio");
        }
        if (sistemaInformatico == null) {
            throw new ValorInvalidoException("El sistema informático es obligatorio");
        }
        numRegistroAcuerdoFacturacion = Textos.opcional(numRegistroAcuerdoFacturacion,
                MAXIMO_LONGITUD_NUM_ACUERDO, "El número de registro del acuerdo de facturación");
        idAcuerdoSistemaInformatico = Textos.opcional(idAcuerdoSistemaInformatico,
                MAXIMO_LONGITUD_ID_ACUERDO, "El identificador del acuerdo de sistema informático");

        facturasRectificadas = listaSegura(
                facturasRectificadas, MAXIMO_FACTURAS_REFERENCIADAS, "Las facturas rectificadas");
        facturasSustituidas = listaSegura(
                facturasSustituidas, MAXIMO_FACTURAS_REFERENCIADAS, "Las facturas sustituidas");
        destinatarios = listaSegura(destinatarios, MAXIMO_DESTINATARIOS, "Los destinatarios");

        // Estos bloques solo se rellenan en el tipo de factura que les corresponde.
        if (!facturasRectificadas.isEmpty() && !tipoFactura.esRectificativa()) {
            throw new ReglaAeatIncumplidaException("1117",
                    "Solo una factura rectificativa puede referenciar facturas rectificadas, y esta es "
                            + tipoFactura.codigo());
        }
        if (!facturasSustituidas.isEmpty() && tipoFactura != TipoFactura.F3) {
            throw new ReglaAeatIncumplidaException("1116",
                    "Solo una factura F3 puede referenciar facturas sustituidas, y esta es "
                            + tipoFactura.codigo());
        }

        // Estas tres reglas provocan el rechazo del registro por la AEAT, así que se validan al construir.
        if (tipoRectificativa == null && tipoFactura.esRectificativa()) {
            throw new ReglaAeatIncumplidaException("1114",
                    "Una factura rectificativa debe declarar si rectifica por sustitución o por "
                            + "diferencias, y esta es " + tipoFactura.codigo());
        }
        if (tipoRectificativa != null && !tipoFactura.esRectificativa()) {
            throw new ReglaAeatIncumplidaException("1115",
                    "Solo una factura rectificativa lleva tipo de rectificativa, y esta es "
                            + tipoFactura.codigo());
        }
        if (!subsanacion && (rechazoPrevio == RechazoPrevio.S || rechazoPrevio == RechazoPrevio.X)) {
            String codigo = rechazoPrevio == RechazoPrevio.S ? "1161" : "1153";
            throw new ReglaAeatIncumplidaException(codigo,
                    "RechazoPrevio = " + rechazoPrevio.codigo() + " solo cabe en una subsanación");
        }
        if (tipoRectificativa != ClaveTipoRectificativa.I
                && tipoFactura != TipoFactura.R2 && tipoFactura != TipoFactura.R3) {
            desglose.detalles().forEach(DetalleDesglose::exigirCuotaCoherenteConLaBase);
        }
    }

    /**
     * Si la AEAT va a contrastar los totales con el desglose. No lo hace cuando alguna línea
     * lleva una clave de régimen exenta de esa comprobación.
     */
    public boolean seContrastanLosTotales() {
        return desglose.detalles().stream()
                .map(DetalleDesglose::claveRegimen)
                .filter(clave -> clave != null)
                .noneMatch(ClaveRegimen::excluyeCuadreDeTotales);
    }

    /**
     * Si {@link #cuotaTotal()} cuadra con el desglose dentro del margen de la AEAT. No lanza: un
     * descuadre es error admisible y no impide emitir.
     */
    public boolean cuadraLaCuotaTotal() {
        return !seContrastanLosTotales() || dentroDelMargen(cuotaTotal, desglose.totalCuotas());
    }

    /** Análogo a {@link #cuadraLaCuotaTotal()} para {@link #importeTotal()}. */
    public boolean cuadraElImporteTotal() {
        return !seContrastanLosTotales() || dentroDelMargen(importeTotal, desglose.totalConImpuestos());
    }

    private static boolean dentroDelMargen(Importe declarado, Importe calculado) {
        Importe diferencia = new Importe(declarado.restar(calculado).valor().abs());
        return diferencia.compareTo(MARGEN_CUADRE) <= 0;
    }

    private static <T> List<T> listaSegura(List<T> lista, int maximo, String campo) {
        if (lista == null) {
            return List.of();
        }
        if (lista.size() > maximo) {
            throw new ValorInvalidoException(
                    campo + " admiten como máximo " + maximo + " elementos y son " + lista.size());
        }
        return List.copyOf(lista);
    }

    public static Builder builder() {
        return new Builder();
    }

    /** Construcción por nombre en lugar de por posición. El constructor canónico sigue validando. */
    public static final class Builder {

        private IdFactura idFactura;
        private String refExterna;
        private String nombreRazonEmisor;
        private boolean subsanacion;
        private RechazoPrevio rechazoPrevio;
        private TipoFactura tipoFactura;
        private ClaveTipoRectificativa tipoRectificativa;
        private List<IdFactura> facturasRectificadas = List.of();
        private List<IdFactura> facturasSustituidas = List.of();
        private ImporteRectificacion importeRectificacion;
        private LocalDate fechaOperacion;
        private String descripcionOperacion;
        private boolean facturaSimplificadaArt7273;
        private boolean facturaSinIdentifDestinatarioArt61d;
        private boolean macrodato;
        private EmitidaPor emitidaPorTerceroODestinatario;
        private PersonaFisicaJuridica tercero;
        private List<PersonaFisicaJuridica> destinatarios = List.of();
        private boolean cupon;
        private Desglose desglose;
        private Importe cuotaTotal;
        private Importe importeTotal;
        private SistemaInformatico sistemaInformatico;
        private String numRegistroAcuerdoFacturacion;
        private String idAcuerdoSistemaInformatico;

        private Builder() {
        }

        public Builder idFactura(IdFactura idFactura) {
            this.idFactura = idFactura;
            return this;
        }

        public Builder refExterna(String refExterna) {
            this.refExterna = refExterna;
            return this;
        }

        public Builder nombreRazonEmisor(String nombreRazonEmisor) {
            this.nombreRazonEmisor = nombreRazonEmisor;
            return this;
        }

        public Builder subsanacion(boolean subsanacion) {
            this.subsanacion = subsanacion;
            return this;
        }

        public Builder rechazoPrevio(RechazoPrevio rechazoPrevio) {
            this.rechazoPrevio = rechazoPrevio;
            return this;
        }

        public Builder tipoFactura(TipoFactura tipoFactura) {
            this.tipoFactura = tipoFactura;
            return this;
        }

        public Builder tipoRectificativa(ClaveTipoRectificativa tipoRectificativa) {
            this.tipoRectificativa = tipoRectificativa;
            return this;
        }

        public Builder facturasRectificadas(List<IdFactura> facturasRectificadas) {
            this.facturasRectificadas = facturasRectificadas;
            return this;
        }

        public Builder facturasSustituidas(List<IdFactura> facturasSustituidas) {
            this.facturasSustituidas = facturasSustituidas;
            return this;
        }

        public Builder importeRectificacion(ImporteRectificacion importeRectificacion) {
            this.importeRectificacion = importeRectificacion;
            return this;
        }

        public Builder fechaOperacion(LocalDate fechaOperacion) {
            this.fechaOperacion = fechaOperacion;
            return this;
        }

        public Builder descripcionOperacion(String descripcionOperacion) {
            this.descripcionOperacion = descripcionOperacion;
            return this;
        }

        public Builder facturaSimplificadaArt7273(boolean facturaSimplificadaArt7273) {
            this.facturaSimplificadaArt7273 = facturaSimplificadaArt7273;
            return this;
        }

        public Builder facturaSinIdentifDestinatarioArt61d(boolean facturaSinIdentifDestinatarioArt61d) {
            this.facturaSinIdentifDestinatarioArt61d = facturaSinIdentifDestinatarioArt61d;
            return this;
        }

        public Builder macrodato(boolean macrodato) {
            this.macrodato = macrodato;
            return this;
        }

        public Builder emitidaPorTerceroODestinatario(EmitidaPor emitidaPorTerceroODestinatario) {
            this.emitidaPorTerceroODestinatario = emitidaPorTerceroODestinatario;
            return this;
        }

        public Builder tercero(PersonaFisicaJuridica tercero) {
            this.tercero = tercero;
            return this;
        }

        public Builder destinatarios(List<PersonaFisicaJuridica> destinatarios) {
            this.destinatarios = destinatarios;
            return this;
        }

        public Builder cupon(boolean cupon) {
            this.cupon = cupon;
            return this;
        }

        public Builder desglose(Desglose desglose) {
            this.desglose = desglose;
            return this;
        }

        public Builder cuotaTotal(Importe cuotaTotal) {
            this.cuotaTotal = cuotaTotal;
            return this;
        }

        public Builder importeTotal(Importe importeTotal) {
            this.importeTotal = importeTotal;
            return this;
        }

        public Builder sistemaInformatico(SistemaInformatico sistemaInformatico) {
            this.sistemaInformatico = sistemaInformatico;
            return this;
        }

        public Builder numRegistroAcuerdoFacturacion(String numRegistroAcuerdoFacturacion) {
            this.numRegistroAcuerdoFacturacion = numRegistroAcuerdoFacturacion;
            return this;
        }

        public Builder idAcuerdoSistemaInformatico(String idAcuerdoSistemaInformatico) {
            this.idAcuerdoSistemaInformatico = idAcuerdoSistemaInformatico;
            return this;
        }

        public DatosRegistroAlta build() {
            return new DatosRegistroAlta(idFactura, refExterna, nombreRazonEmisor, subsanacion,
                    rechazoPrevio, tipoFactura, tipoRectificativa, facturasRectificadas,
                    facturasSustituidas, importeRectificacion, fechaOperacion, descripcionOperacion,
                    facturaSimplificadaArt7273, facturaSinIdentifDestinatarioArt61d, macrodato,
                    emitidaPorTerceroODestinatario, tercero, destinatarios, cupon, desglose,
                    cuotaTotal, importeTotal, sistemaInformatico, numRegistroAcuerdoFacturacion,
                    idAcuerdoSistemaInformatico);
        }
    }
}
