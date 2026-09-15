package dev.lacre.verifactu.registro;

import dev.lacre.shared.Huella;
import dev.lacre.shared.Importe;
import dev.lacre.shared.Nif;
import dev.lacre.shared.Porcentaje;
import dev.lacre.verifactu.desglose.CalificacionOperacion;
import dev.lacre.verifactu.desglose.ClaveRegimen;
import dev.lacre.verifactu.desglose.Desglose;
import dev.lacre.verifactu.desglose.DetalleDesglose;
import dev.lacre.verifactu.desglose.Impuesto;

import java.time.LocalDate;
import java.util.List;

/**
 * Datos de partida para los tests, tomados del ejemplo oficial de la huella. Lo que no entra en
 * la huella se rellena con valores fijos.
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

    /** Enlace con un registro anterior ficticio, para probar la cadena. */
    public static RegistroAnterior anterior(Huella huella) {
        return new RegistroAnterior(idFactura("12345678/G32"), huella);
    }

    /** Anulación del ejemplo oficial de la huella. */
    public static DatosRegistroAnulacion anulacion() {
        return new DatosRegistroAnulacion(
                idFactura("12345679/G34"), null, false, false, null, null,
                sistemaInformatico());
    }

    public static IdFactura idFactura(String numSerie) {
        return new IdFactura(EMISOR, numSerie, FECHA_EXPEDICION);
    }

    /**
     * Desglose que cuadra con los totales del ejemplo oficial, 111,10 + 12,35 = 123,45, y cuya
     * cuota es la del 10 % dentro del margen de la AEAT.
     */
    public static Desglose desglose() {
        return Desglose.de(new DetalleDesglose(
                Impuesto.IVA, new ClaveRegimen("01"), CalificacionOperacion.S1,
                Porcentaje.de("10"), Importe.de("111.10"), null, Importe.de("12.35"), null, null));
    }

    public static SistemaInformatico sistemaInformatico() {
        return new SistemaInformatico(
                new PersonaFisicaJuridica("lacre", new Nif("12345678Z")),
                "lacre", "01", "0.0.1", "0001",
                true, false, false);
    }
}
