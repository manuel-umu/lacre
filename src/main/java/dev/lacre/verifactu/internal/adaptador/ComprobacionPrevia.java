package dev.lacre.verifactu.internal.adaptador;

import dev.lacre.shared.Huella;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.EnumSet;
import java.util.Set;

/**
 * Comprobación previa del art. 7.i de la OM HAC/1177/2024: antes de generar un registro que no
 * sea el primero, hay que verificar el estado del último ya generado.
 * <p>
 * Son dos requisitos, y el segundo se lee al revés de lo que parece:
 * <ol>
 * <li>El último registro está correctamente encadenado con el anterior a él.</li>
 * <li>Su fecha y hora de generación <strong>no es superior en más de un minuto</strong> a la
 *     que se va a usar para fechar el nuevo. La FAQ 15 lo aclara: que el nuevo sea posterior en
 *     más de un minuto es lo normal y no es problema; lo inadmisible es que el anterior venga
 *     del futuro.</li>
 * </ol>
 * <p>
 * <strong>Detectar una anomalía no impide emitir</strong>, y no es una laxitud nuestra: la
 * FAQ 15 es explícita en que «será preciso generar el siguiente RF, ya que la facturación por
 * este motivo NUNCA debe interrumpirse». Por eso esto devuelve un conjunto de anomalías en vez
 * de lanzar, igual que el cuadre de totales de {@code DatosRegistroAlta}.
 * <p>
 * Lógica pura y sin estado: vive junto al caso de uso porque habla de filas ya guardadas, no del
 * modelo fiscal que se publica en Maven Central.
 */
final class ComprobacionPrevia {

    /**
     * «No superior en más de un minuto»: exactamente un minuto de adelanto todavía se admite.
     */
    private static final Duration ADELANTO_MAXIMO = Duration.ofMinutes(1);

    enum Anomalia {

        /** El último registro no enlaza con la huella del que le precede. */
        HUELLA_ANTERIOR_NO_CUADRA,

        /** El último registro se generó con más de un minuto de adelanto sobre el reloj actual. */
        FECHA_DEL_ANTERIOR_EN_EL_FUTURO
    }

    private ComprobacionPrevia() {
    }

    /**
     * @param ultimo el RF n-1, el que precede al que se va a generar
     * @param penultimo el RF n-2, o {@code null} si el último abre la cadena
     * @param ahora la fecha y hora que se usará para fechar el registro nuevo
     */
    static Set<Anomalia> comprobar(CadenaDeRegistros.Enlace ultimo, CadenaDeRegistros.Enlace penultimo,
                                   OffsetDateTime ahora) {
        Set<Anomalia> anomalias = EnumSet.noneOf(Anomalia.class);

        if (!enlazaCon(ultimo, penultimo)) {
            anomalias.add(Anomalia.HUELLA_ANTERIOR_NO_CUADRA);
        }
        if (ultimo.fechaHora().isAfter(ahora.plus(ADELANTO_MAXIMO))) {
            anomalias.add(Anomalia.FECHA_DEL_ANTERIOR_EN_EL_FUTURO);
        }
        return anomalias;
    }

    /**
     * El primero de la cadena está bien encadenado precisamente por no llevar huella anterior;
     * cualquier otro debe llevar exactamente la del que le precede.
     */
    private static boolean enlazaCon(CadenaDeRegistros.Enlace ultimo, CadenaDeRegistros.Enlace penultimo) {
        Huella declarada = ultimo.huellaAnterior();
        return penultimo == null
                ? declarada == null
                : declarada != null && declarada.equals(penultimo.huella());
    }
}
