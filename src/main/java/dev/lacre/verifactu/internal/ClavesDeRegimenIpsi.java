package dev.lacre.verifactu.internal;

import dev.lacre.shared.ReglaAeatIncumplidaException;
import dev.lacre.verifactu.desglose.ClaveRegimen;
import dev.lacre.verifactu.desglose.DetalleDesglose;
import dev.lacre.verifactu.desglose.Impuesto;
import dev.lacre.verifactu.registro.DatosRegistroAlta;
import java.time.LocalDate;
import java.util.Set;

/** Claves de régimen que admite una línea de IPSI, exigidas por la AEAT desde el 01-01-2027. */
public final class ClavesDeRegimenIpsi {

    public static final LocalDate EXIGIBLES_DESDE = LocalDate.of(2027, 1, 1);

    /** Régimen general, IGIC o IVA, arrendamiento, art. 73.4 y 5 de Ceuta, exentas y estimación objetiva. */
    public static final Set<String> ADMITIDAS = Set.of("01", "08", "11", "18", "19", "20");

    private ClavesDeRegimenIpsi() {}

    public static void exigir(DatosRegistroAlta datos, LocalDate hoy) {
        if (hoy.isBefore(EXIGIBLES_DESDE)) {
            return;
        }
        for (DetalleDesglose detalle : datos.desglose().detalles()) {
            if (detalle.impuesto() != Impuesto.IPSI) {
                continue;
            }
            ClaveRegimen clave = detalle.claveRegimen();
            ReglaAeatIncumplidaException.exigir(
                    clave != null, "1245", "Con IPSI la clave de régimen es obligatoria desde el " + EXIGIBLES_DESDE);
            ReglaAeatIncumplidaException.exigir(
                    ADMITIDAS.contains(clave.codigo()),
                    "1246",
                    "Con IPSI la clave de régimen tiene que ser 01, 08, 11, 18, 19 o 20, y es " + clave.codigo());
        }
    }
}
