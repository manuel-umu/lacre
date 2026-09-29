package dev.lacre.verifactu.internal;

import dev.lacre.shared.ReglaAeatIncumplidaException;
import dev.lacre.verifactu.registro.DatosRegistro;
import dev.lacre.verifactu.registro.DatosRegistroAlta;
import dev.lacre.verifactu.registro.DatosRegistroAnulacion;
import dev.lacre.verifactu.registro.RechazoPrevio;

/**
 * Operativas de alta y anulación que la AEAT rechaza cuando ya tiene un registro de la misma
 * factura, del anexo de operativas admisibles de sus validaciones.
 */
public final class OperativaAeat {

    /** Registro de facturación duplicado, en el catálogo de errores de la AEAT. */
    public static final String REGISTRO_DUPLICADO = "3000";

    private OperativaAeat() {}

    /** Exige que la operativa admita una factura que ya tiene un registro de alta o anulación. */
    public static void exigir(DatosRegistro datos, boolean laFacturaYaTieneRegistro) {
        if (!laFacturaYaTieneRegistro) {
            return;
        }
        String yaExiste = "La factura " + datos.idFactura().numSerieFactura() + " ya tiene un registro: ";
        switch (datos) {
            case DatosRegistroAlta alta -> {
                ReglaAeatIncumplidaException.exigir(
                        alta.subsanacion(),
                        REGISTRO_DUPLICADO,
                        yaExiste + "un alta nueva tiene que ser una subsanación");
                ReglaAeatIncumplidaException.exigir(
                        alta.rechazoPrevio() != RechazoPrevio.X,
                        REGISTRO_DUPLICADO,
                        yaExiste + "RechazoPrevio = X solo cabe si no existe en la AEAT");
            }
            case DatosRegistroAnulacion anulacion ->
                ReglaAeatIncumplidaException.exigir(
                        !anulacion.sinRegistroPrevio(),
                        REGISTRO_DUPLICADO,
                        yaExiste + "la anulación no puede declararse sin registro previo");
        }
    }
}
