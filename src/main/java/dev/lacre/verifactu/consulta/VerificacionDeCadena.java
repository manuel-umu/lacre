package dev.lacre.verifactu.consulta;

import java.util.List;
import java.util.UUID;

/**
 * Resultado de recorrer entera la cadena de un obligado buscando roturas.
 * <p>
 * <strong>Comprueba los enlaces, no recalcula las huellas</strong>, y la diferencia importa: esto
 * detecta que a un registro le hayan cambiado la huella o que falte un eslabón, pero no que le
 * hayan cambiado el contenido recalculando la huella en consecuencia. Para eso haría falta
 * reconstruir la cadena canónica de cada registro a partir de su XML guardado, y hoy no existe
 * lector de XML, solo escritor.
 * <p>
 * Por eso {@link #alcance()} viaja en la respuesta en vez de dejar que quien la lea suponga que
 * la verificación es total.
 *
 * @param registros cuántos tiene la cadena
 * @param roturas vacía si todo encaja; en orden de posición
 *
 * @implNote TODO Fase 8.4: recalcular la huella de cada registro exige un lector del XML
 * guardado. Cuando exista, {@code alcance} pasará a {@code HUELLAS} y esta clase dejará de
 * mentir por omisión.
 */
public record VerificacionDeCadena(
        UUID obligadoId, long registros, List<Rotura> roturas, Alcance alcance) {

    public VerificacionDeCadena {
        roturas = List.copyOf(roturas);
    }

    public boolean intacta() {
        return roturas.isEmpty();
    }

    /** Hasta dónde llega la comprobación. Existe para que la respuesta no prometa de más. */
    public enum Alcance {

        /** Solo se ha comprobado que cada eslabón enlaza con el anterior y que no faltan. */
        ENLACES,

        /** Además se ha recalculado la huella de cada registro. Todavía no se produce. */
        HUELLAS
    }

    /** Un eslabón que no encaja, con el motivo por el que no encaja. */
    public record Rotura(long posicion, Motivo motivo) {

        public enum Motivo {

            /** La huella anterior declarada no es la del registro que le precede. */
            HUELLA_ANTERIOR_NO_CUADRA,

            /** Falta al menos una posición entre este registro y el anterior. */
            POSICION_SALTADA,

            /** No es el primero de la cadena y aun así no declara huella anterior. */
            SIN_HUELLA_ANTERIOR,

            /** Abre la cadena y declara una huella anterior que no debería existir. */
            PRIMERO_CON_HUELLA_ANTERIOR
        }
    }
}
