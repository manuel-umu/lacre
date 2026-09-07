package dev.lacre.sif.registro;

import dev.lacre.shared.Importe;
import dev.lacre.shared.Textos;
import dev.lacre.shared.ValorInvalidoException;
import dev.lacre.sif.desglose.ClaveRegimen;
import dev.lacre.sif.desglose.Desglose;
import dev.lacre.sif.desglose.DetalleDesglose;
import dev.lacre.sif.huella.Canonicalizador;
import dev.lacre.sif.huella.EncadenadorRegistros;

import java.time.LocalDate;
import java.util.List;

/**
 * Contenido de un registro de facturación de alta, {@code RegistroFacturacionAltaType} del XSD.
 * <p>
 * No incluye lo que no es dato de entrada: el encadenamiento, la fecha y hora de generación y
 * la huella los produce {@link EncadenadorRegistros} y viven en {@link RegistroEncadenado}.
 * {@code IDVersion} y {@code TipoHuella} son constantes y tampoco ocupan un componente.
 * <p>
 * El orden en que se declaran los componentes sigue al del XSD por comodidad al serializar,
 * pero <strong>no tiene significado normativo</strong>: el orden que importa es el de la
 * concatenación para la huella, y ese vive en {@link Canonicalizador}.
 * <p>
 * Con veintitrés componentes, construirlo posicionalmente es un riesgo real —dos {@link Importe}
 * intercambiados compilan y fallan en silencio—, así que lo normal es usar {@link #builder()}.
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
        SistemaInformatico sistemaInformatico) {

    /** Única versión admitida por {@code VersionType}. */
    public static final String ID_VERSION = "1.0";

    /** Único algoritmo admitido por {@code TipoHuellaType}: SHA-256. */
    public static final String TIPO_HUELLA = "01";

    public static final int MAXIMO_LONGITUD_REF_EXTERNA = 60;
    public static final int MAXIMO_LONGITUD_NOMBRE_EMISOR = 120;
    public static final int MAXIMO_LONGITUD_DESCRIPCION = 500;
    public static final int MAXIMO_FACTURAS_REFERENCIADAS = 1000;
    public static final int MAXIMO_DESTINATARIOS = 1000;

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

        facturasRectificadas = listaSegura(
                facturasRectificadas, MAXIMO_FACTURAS_REFERENCIADAS, "Las facturas rectificadas");
        facturasSustituidas = listaSegura(
                facturasSustituidas, MAXIMO_FACTURAS_REFERENCIADAS, "Las facturas sustituidas");
        destinatarios = listaSegura(destinatarios, MAXIMO_DESTINATARIOS, "Los destinatarios");

        // El XSD lo dice en sus propias anotaciones: estos bloques «únicamente se rellenan» en
        // el caso al que corresponden.
        if (!facturasRectificadas.isEmpty() && !tipoFactura.esRectificativa()) {
            throw new ValorInvalidoException(
                    "Solo una factura rectificativa puede referenciar facturas rectificadas, y esta es "
                            + tipoFactura.codigo());
        }
        if (!facturasSustituidas.isEmpty() && tipoFactura != TipoFactura.F3) {
            throw new ValorInvalidoException(
                    "Solo una factura F3 puede referenciar facturas sustituidas, y esta es "
                            + tipoFactura.codigo());
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
     * Si {@link #cuotaTotal()} cuadra con el desglose dentro del margen de la AEAT.
     * <p>
     * Devuelve un booleano y no lanza a propósito: un descuadre <strong>no impide emitir</strong>.
     * La AEAT lo trata como error admisible —el registro queda «Aceptado con errores»— y la
     * norma es explícita en que la facturación nunca debe interrumpirse. Quien llame decide si
     * avisa, registra o corrige.
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

    /**
     * Construcción por nombre en lugar de por posición.
     * <p>
     * No es azúcar: con veintitrés componentes, y varios del mismo tipo, un intercambio de
     * argumentos compila sin ruido y produce un registro fiscalmente incorrecto. Los campos
     * obligatorios los sigue validando el constructor canónico del record, así que un
     * {@code build()} incompleto falla igualmente.
     */
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

        public DatosRegistroAlta build() {
            return new DatosRegistroAlta(idFactura, refExterna, nombreRazonEmisor, subsanacion,
                    rechazoPrevio, tipoFactura, tipoRectificativa, facturasRectificadas,
                    facturasSustituidas, importeRectificacion, fechaOperacion, descripcionOperacion,
                    facturaSimplificadaArt7273, facturaSinIdentifDestinatarioArt61d, macrodato,
                    emitidaPorTerceroODestinatario, tercero, destinatarios, cupon, desglose,
                    cuotaTotal, importeTotal, sistemaInformatico);
        }
    }
}
