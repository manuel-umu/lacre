package dev.lacre.verifactu.registro;

import dev.lacre.shared.IdOtro;
import dev.lacre.shared.Importe;
import dev.lacre.shared.Nif;
import dev.lacre.shared.Porcentaje;
import dev.lacre.shared.ReglaAeatIncumplidaException;
import dev.lacre.shared.Textos;
import dev.lacre.shared.TipoIdentificacion;
import dev.lacre.shared.ValorInvalidoException;
import dev.lacre.verifactu.desglose.CalificacionOperacion;
import dev.lacre.verifactu.desglose.ClaveRegimen;
import dev.lacre.verifactu.desglose.Desglose;
import dev.lacre.verifactu.desglose.DetalleDesglose;
import dev.lacre.verifactu.desglose.Impuesto;
import dev.lacre.verifactu.huella.Canonicalizador;
import dev.lacre.verifactu.huella.EncadenadorRegistros;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static dev.lacre.shared.ReglaAeatIncumplidaException.exigir;

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

    /** Importe total, en valor absoluto, a partir del cual el registro es macrodato. */
    public static final Importe UMBRAL_MACRODATO = Importe.de("100000000.00");

    /** Bases y cuotas repercutidas que admite una F2 sin acuerdo de facturación. */
    public static final Importe LIMITE_SIMPLIFICADA = Importe.de("3000.00");

    private static final Set<TipoFactura> CON_INVERSION_DEL_SUJETO_PASIVO = Set.of(TipoFactura.F1,
            TipoFactura.F3, TipoFactura.R1, TipoFactura.R2, TipoFactura.R3, TipoFactura.R4);

    private static final Set<TipoFactura> SIN_GRUPO_DE_ENTIDADES =
            Set.of(TipoFactura.F2, TipoFactura.F3, TipoFactura.R5);

    private static final Set<TipoFactura> CON_DEVENGO_PENDIENTE_EN_OBRA = Set.of(TipoFactura.F1,
            TipoFactura.R1, TipoFactura.R2, TipoFactura.R3, TipoFactura.R4);

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

        exigir(tipoRectificativa != ClaveTipoRectificativa.S || importeRectificacion != null,
                "1118", "Una rectificativa por sustitución debe informar el importe de la "
                        + "rectificación");
        exigir(importeRectificacion == null || tipoRectificativa == ClaveTipoRectificativa.S,
                "1119", "Solo una rectificativa por sustitución informa el importe de la "
                        + "rectificación");

        boolean simplificada = tipoFactura == TipoFactura.F2 || tipoFactura == TipoFactura.R5;
        exigir(!facturaSimplificadaArt7273 || !simplificada, "1183", "La marca de factura "
                + "simplificada de los artículos 7.2 y 7.3 no cabe en una "
                + tipoFactura.codigo());
        exigir(!facturaSinIdentifDestinatarioArt61d || simplificada, "1185", "La marca de factura "
                + "sin identificación del destinatario del artículo 6.1.d) solo cabe en una F2 o "
                + "R5, y esta es " + tipoFactura.codigo());
        exigir(!cupon || tipoFactura == TipoFactura.R1 || tipoFactura == TipoFactura.R5, "1157",
                "La marca de cupón solo cabe en una R1 o R5, y esta es " + tipoFactura.codigo());
        boolean superaElUmbral =
                importeTotal.valor().abs().compareTo(UMBRAL_MACRODATO.valor()) >= 0;
        exigir(macrodato || !superaElUmbral, "1139", "Un importe total de "
                + importeTotal.valor().toPlainString() + " exige la marca de macrodato");
        exigir(!macrodato || superaElUmbral, "1138", "La marca de macrodato solo cabe con un "
                + "importe total de 100.000.000 o más en valor absoluto");

        exigir(emitidaPorTerceroODestinatario != EmitidaPor.D || !destinatarios.isEmpty(), "1158",
                "Una factura emitida por el destinatario (D) debe llevar destinatarios");
        exigir(!destinatarios.isEmpty() || simplificada, "1189",
                "Una factura " + tipoFactura.codigo() + " debe llevar al menos un destinatario");
        exigir(destinatarios.isEmpty() || !simplificada, "1190",
                "Una factura " + tipoFactura.codigo() + " no lleva destinatarios");
        destinatarios.forEach(PersonaFisicaJuridica::exigirComoDestinatario);
        exigirIdentificacionDeDestinatarios(tipoFactura, destinatarios);

        if (tercero != null) {
            exigir(emitidaPorTerceroODestinatario != null, "1155",
                    "El tercero solo se informa si la factura la emite un tercero (T)");
            exigir(emitidaPorTerceroODestinatario != EmitidaPor.D, "1159",
                    "Una factura emitida por el destinatario (D) no lleva tercero");
            tercero.exigirComoTercero(idFactura.emisor());
        }
        exigir(emitidaPorTerceroODestinatario != EmitidaPor.T || tercero != null, "1186",
                "Una factura emitida por un tercero (T) debe informar el tercero");

        if (tipoFactura == TipoFactura.F2 && numRegistroAcuerdoFacturacion == null
                && !facturaSinIdentifDestinatarioArt61d) {
            Importe importe = desglose.detalles().stream()
                    .map(detalle -> detalle.baseImponibleOimporteNoSujeto()
                            .sumar(oCero(detalle.cuotaRepercutida())))
                    .reduce(Importe.CERO, Importe::sumar);
            exigir(importe.compareTo(LIMITE_SIMPLIFICADA.sumar(MARGEN_CUADRE)) <= 0, "1150",
                    "Una F2 no puede superar " + LIMITE_SIMPLIFICADA.valor().toPlainString()
                            + " € de bases y cuotas repercutidas, con "
                            + MARGEN_CUADRE.valor().toPlainString() + " € de margen, y suma "
                            + importe.valor().toPlainString());
        }

        exigirClavesDeRegimen(tipoFactura, desglose, destinatarios, idFactura.fechaExpedicion(),
                fechaOperacion);
        exigirTiposConVigencia(desglose,
                fechaOperacion != null ? fechaOperacion : idFactura.fechaExpedicion());
    }

    /** Una R3 identifica al destinatario con NIF o no censado; una R2, también con NIF-IVA. */
    private static void exigirIdentificacionDeDestinatarios(
            TipoFactura tipoFactura, List<PersonaFisicaJuridica> destinatarios) {
        for (PersonaFisicaJuridica destinatario : destinatarios) {
            if (!(destinatario.identificador() instanceof IdOtro otro)) {
                continue;
            }
            exigir(tipoFactura != TipoFactura.R3 || otro.tipo() == TipoIdentificacion.NO_CENSADO,
                    "1191", "Una R3 identifica al destinatario con NIF o como no censado (07), y "
                            + "es " + otro.tipo().codigo());
            exigir(tipoFactura != TipoFactura.R2 || otro.tipo() == TipoIdentificacion.NO_CENSADO
                            || otro.tipo() == TipoIdentificacion.NIF_IVA, "1192",
                    "Una R2 identifica al destinatario con NIF, NIF-IVA (02) o como no censado "
                            + "(07), y es " + otro.tipo().codigo());
        }
    }

    /**
     * Lo que cada clave de régimen exige al registro: tipo de factura, destinatarios y fecha de
     * operación. Solo en las líneas de IVA o IGIC, salvo la inversión del sujeto pasivo.
     */
    private static void exigirClavesDeRegimen(TipoFactura tipoFactura, Desglose desglose,
                                              List<PersonaFisicaJuridica> destinatarios,
                                              LocalDate expedicion, LocalDate operacion) {
        boolean hayIvaOIgic = false;
        boolean hayDevengoPendiente = false;
        for (DetalleDesglose detalle : desglose.detalles()) {
            exigir(detalle.calificacion() != CalificacionOperacion.S2
                            || CON_INVERSION_DEL_SUJETO_PASIVO.contains(tipoFactura), "1197",
                    "Con inversión del sujeto pasivo (S2) la factura solo puede ser F1, F3, R1, "
                            + "R2, R3 o R4, y es " + tipoFactura.codigo());
            if (!esIvaOIgic(detalle.impuesto())) {
                continue;
            }
            hayIvaOIgic = true;
            String clave = detalle.claveRegimen() == null ? "" : detalle.claveRegimen().codigo();
            hayDevengoPendiente |= clave.equals("14") || clave.equals("15");
            switch (clave) {
                case "06" -> exigir(!SIN_GRUPO_DE_ENTIDADES.contains(tipoFactura), "1202",
                        "Con clave de régimen 06 la factura no puede ser F2, F3 ni R5, y es "
                                + tipoFactura.codigo());
                case "10" -> {
                    exigir(tipoFactura == TipoFactura.F1, "1205", "Con clave de régimen 10 la "
                            + "factura tiene que ser F1, y es " + tipoFactura.codigo());
                    exigir(destinatarios.stream().allMatch(d -> d.identificador() instanceof Nif),
                            "1205", "Con clave de régimen 10 todos los destinatarios se "
                                    + "identifican con NIF");
                }
                case "14" -> {
                    exigir(CON_DEVENGO_PENDIENTE_EN_OBRA.contains(tipoFactura), "1148",
                            "Con clave de régimen 14 la factura tiene que ser F1, R1, R2, R3 o "
                                    + "R4, y es " + tipoFactura.codigo());
                    exigir(operacion != null && operacion.isAfter(expedicion), "1147",
                            "Con clave de régimen 14 la fecha de operación es obligatoria y "
                                    + "posterior a la de expedición");
                    exigir(destinatarios.stream().allMatch(DatosRegistroAlta::esAdministracion),
                            "1149", "Con clave de régimen 14 todos los destinatarios se "
                                    + "identifican con un NIF que empieza por P, Q, S o V");
                }
                default -> {
                }
            }
        }
        exigir(!hayIvaOIgic || operacion == null || !expedicion.isBefore(operacion)
                        || hayDevengoPendiente, "1146",
                "La fecha de expedición solo puede ser anterior a la de operación con clave de "
                        + "régimen 14 o 15");
    }

    /** El 5 % de IVA, y el 2 % y el 7,5 %, solo en los periodos en que estuvieron vigentes. */
    private static void exigirTiposConVigencia(Desglose desglose, LocalDate referencia) {
        for (DetalleDesglose detalle : desglose.detalles()) {
            Impuesto impuesto = detalle.impuesto();
            if ((impuesto != null && impuesto != Impuesto.IVA)
                    || detalle.calificacion() != CalificacionOperacion.S1
                    || detalle.tipoImpositivo() == null) {
                continue;
            }
            Porcentaje tipo = detalle.tipoImpositivo();
            if (tipo.equals(Porcentaje.de("5"))) {
                exigir(!referencia.isBefore(LocalDate.of(2022, 7, 1))
                                && !referencia.isAfter(LocalDate.of(2024, 9, 30)), "1194",
                        "El tipo del 5 % solo se admite con fecha de operación, o de expedición "
                                + "si no la hay, del 01-07-2022 al 30-09-2024, y es " + referencia);
            }
            if ((tipo.equals(Porcentaje.de("2")) || tipo.equals(Porcentaje.de("7.5")))
                    && (referencia.isBefore(LocalDate.of(2024, 10, 1))
                    || referencia.isAfter(LocalDate.of(2024, 12, 31)))) {
                throw new ValorInvalidoException("El tipo del "
                        + tipo.valor().stripTrailingZeros().toPlainString() + " % solo se admite "
                        + "con fecha de operación, o de expedición si no la hay, del 01-10-2024 "
                        + "al 31-12-2024, y es " + referencia);
            }
        }
    }

    private static boolean esIvaOIgic(Impuesto impuesto) {
        return impuesto == null || impuesto == Impuesto.IVA || impuesto == Impuesto.IGIC;
    }

    private static boolean esAdministracion(PersonaFisicaJuridica destinatario) {
        return destinatario.identificador() instanceof Nif nif
                && "PQSV".indexOf(nif.valor().charAt(0)) >= 0;
    }

    private static Importe oCero(Importe importe) {
        return importe == null ? Importe.CERO : importe;
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
