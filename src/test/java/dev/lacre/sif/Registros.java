package dev.lacre.sif;

import dev.lacre.shared.Importe;
import dev.lacre.shared.Nif;
import dev.lacre.shared.Porcentaje;

import java.time.LocalDate;
import java.util.List;

/**
 * Datos de partida para los tests, tomados del ejemplo del apartado 6 del documento de la
 * huella de la AEAT.
 * <p>
 * Todo lo que no entra en el cálculo de la huella —desglose, sistema informático, nombre del
 * emisor— se rellena con valores fijos, de modo que los tests de conformidad puedan cambiar
 * solo los ocho campos que sí entran.
 */
public final class Registros {

    public static final Nif EMISOR = new Nif("89890001K");
    public static final LocalDate FECHA_EXPEDICION = LocalDate.of(2024, 1, 1);

    private Registros() {
    }

    /** Builder con todos los campos obligatorios ya puestos, listo para sobrescribir. */
    public static DatosRegistroAlta.Builder alta() {
        return DatosRegistroAlta.builder()
                .idFactura(new IdFactura(EMISOR, "12345678/G33", FECHA_EXPEDICION))
                .nombreRazonEmisor("Obligado de prueba SL")
                .tipoFactura(TipoFactura.F1)
                .descripcionOperacion("Servicios de consultoría")
                .destinatarios(List.of(new PersonaFisicaJuridica("Cliente SL", new Nif("A28015865"))))
                .desglose(desglose())
                .cuotaTotal(Importe.de("12.35"))
                .importeTotal(Importe.de("123.45"))
                .sistemaInformatico(sistemaInformatico());
    }

    public static IdFactura idFactura(String numSerie) {
        return new IdFactura(EMISOR, numSerie, FECHA_EXPEDICION);
    }

    /** Desglose que cuadra con los totales del ejemplo oficial: 111,10 + 12,35 = 123,45. */
    public static Desglose desglose() {
        return Desglose.de(new DetalleDesglose(
                Impuesto.IVA, new ClaveRegimen("01"), CalificacionOperacion.S1,
                Porcentaje.de("21"), Importe.de("111.10"), null, Importe.de("12.35"), null, null));
    }

    public static SistemaInformatico sistemaInformatico() {
        return new SistemaInformatico(
                new PersonaFisicaJuridica("lacre", new Nif("12345678Z")),
                "lacre", "01", "0.0.1", "0001",
                true, false, false);
    }
}
