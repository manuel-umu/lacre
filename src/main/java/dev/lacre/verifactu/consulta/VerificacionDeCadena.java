package dev.lacre.verifactu.consulta;

import java.util.List;
import java.util.UUID;

/**
 * Resultado de recorrer la cadena de un obligado buscando roturas: en los enlaces entre
 * eslabones y, según {@link #alcance()}, en la huella de cada registro.
 *
 * @param registros cuántos tiene la cadena
 * @param roturas vacía si todo encaja; en orden de posición
 */
public record VerificacionDeCadena(
        UUID obligadoId, long registros, List<Rotura> roturas, Alcance alcance) {

    public VerificacionDeCadena {
        roturas = List.copyOf(roturas);
    }

    public boolean intacta() {
        return roturas.isEmpty();
    }

    /** Hasta dónde llega la comprobación. */
    public enum Alcance {

        /** Solo se ha comprobado que cada eslabón enlaza con el anterior y que no faltan. */
        ENLACES,

        /** Además se ha recalculado la huella de cada registro. */
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
            PRIMERO_CON_HUELLA_ANTERIOR,

            /** El contenido de su XML no produce la huella guardada. */
            HUELLA_NO_CUADRA,

            /** Su XML no se puede leer, así que no hay forma de recalcular su huella. */
            XML_ILEGIBLE
        }
    }
}
