package dev.lacre.verifactu.internal.adaptador;

import dev.lacre.shared.Huella;
import dev.lacre.verifactu.emision.AnomaliaPrevia;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.EnumSet;
import java.util.Set;

/**
 * Comprobación previa del art. 7.i de la OM HAC/1177/2024: el último registro debe estar
 * correctamente encadenado con el anterior, y su fecha de generación no debe superar en más de
 * un minuto a la actual. Devuelve las anomalías en vez de lanzar, porque no impiden emitir.
 */
final class ComprobacionPrevia {

    /** Exactamente un minuto de adelanto todavía se admite. */
    private static final Duration ADELANTO_MAXIMO = Duration.ofMinutes(1);

    private ComprobacionPrevia() {
    }

    /**
     * @param ultimo    el registro que precede al que se va a generar
     * @param penultimo el anterior a {@code ultimo}, o {@code null} si este abre la cadena
     * @param ahora     fecha y hora con la que se fechará el registro nuevo
     */
    static Set<AnomaliaPrevia> comprobar(CadenaDeRegistros.Enlace ultimo,
                                         CadenaDeRegistros.Enlace penultimo, OffsetDateTime ahora) {
        Set<AnomaliaPrevia> anomalias = EnumSet.noneOf(AnomaliaPrevia.class);

        if (!enlazaCon(ultimo, penultimo)) {
            anomalias.add(AnomaliaPrevia.HUELLA_ANTERIOR_NO_CUADRA);
        }
        if (ultimo.fechaHora().isAfter(ahora.plus(ADELANTO_MAXIMO))) {
            anomalias.add(AnomaliaPrevia.FECHA_DEL_ANTERIOR_EN_EL_FUTURO);
        }
        return anomalias;
    }

    /** El primero de la cadena no lleva huella anterior; cualquier otro lleva la del que le precede. */
    private static boolean enlazaCon(CadenaDeRegistros.Enlace ultimo, CadenaDeRegistros.Enlace penultimo) {
        Huella declarada = ultimo.huellaAnterior();
        return penultimo == null
                ? declarada == null
                : declarada != null && declarada.equals(penultimo.huella());
    }
}
