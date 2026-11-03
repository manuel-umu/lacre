package dev.lacre.api.internal.emision;

import dev.lacre.verifactu.registro.DatosRegistro;
import dev.lacre.verifactu.registro.DatosRegistroAlta;
import java.util.ArrayList;
import java.util.List;

/** Descuadre de los totales de un alta con su desglose. No impide registrar: la AEAT lo acepta con errores. */
enum Descuadre {
    CUOTA_TOTAL_NO_CUADRA("La cuota total declarada no coincide con la suma de las cuotas del desglose "
            + "dentro del margen de " + DatosRegistroAlta.MARGEN_CUADRE.valor() + " €. El registro se ha creado, "
            + "pero la AEAT lo aceptará con errores."),

    IMPORTE_TOTAL_NO_CUADRA("El importe total declarado no coincide con las bases más las cuotas del desglose "
            + "dentro del margen de " + DatosRegistroAlta.MARGEN_CUADRE.valor() + " €. El registro se ha creado, "
            + "pero la AEAT lo aceptará con errores.");

    private final String mensaje;

    Descuadre(String mensaje) {
        this.mensaje = mensaje;
    }

    String mensaje() {
        return mensaje;
    }

    /** Los descuadres de un alta; una anulación no declara totales. */
    static List<Descuadre> de(DatosRegistro datos) {
        List<Descuadre> descuadres = new ArrayList<>();
        if (datos instanceof DatosRegistroAlta alta) {
            if (!alta.cuadraLaCuotaTotal()) {
                descuadres.add(CUOTA_TOTAL_NO_CUADRA);
            }
            if (!alta.cuadraElImporteTotal()) {
                descuadres.add(IMPORTE_TOTAL_NO_CUADRA);
            }
        }
        return descuadres;
    }
}
