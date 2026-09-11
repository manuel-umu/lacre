package dev.lacre.api.internal.emision;

import dev.lacre.verifactu.registro.DatosRegistro;
import dev.lacre.verifactu.registro.SistemaInformatico;

/**
 * Lo que el ERP pide: dar de alta un registro o anular una factura.
 * <p>
 * <strong>Era una interfaz sellada y ya no lo es</strong>, y conviene saber por qué. Una
 * jerarquía sellada y sus subtipos permitidos deben vivir en el mismo paquete mientras el
 * proyecto no sea un módulo JPMS —la misma obligación del lenguaje que mantiene juntas a
 * {@code Calificacion} y sus dos implementaciones—, así que sellar y repartir {@code alta} y
 * {@code anulacion} en subpaquetes son cosas incompatibles.
 * <p>
 * Lo que el sellado daba era que el compilador no dejase olvidarse de un tipo nuevo en los dos
 * sitios que dependen de él: el mapeo al dominio y la huella de idempotencia. Eso <strong>no se
 * ha perdido</strong>, solo ha cambiado de mecanismo: en vez de dos {@code switch} exhaustivos
 * sobre el tipo, son dos métodos de esta interfaz. Una tercera clase de petición tampoco compila
 * hasta resolver los dos.
 * <p>
 * Para {@code Emisiones} las dos son el mismo caso de uso: mismo cerrojo, misma cadena, misma
 * tabla de idempotencia y misma respuesta. Lo que cambia es el contenido del registro.
 */
public interface PeticionRegistro {

    /** La factura que la petición identifica: la que se expide, o la que se anula. */
    IdFacturaDto factura();

    /**
     * Traduce el contrato al modelo fiscal. El sistema informático lo pone quien llama y no el
     * ERP: es el dato que respalda nuestra declaración responsable.
     */
    DatosRegistro aDatos(SistemaInformatico sistemaInformatico);

    /**
     * Lo que distingue esta petición de otra sobre la misma factura, para la huella de
     * idempotencia. La misma factura puede tener alta y anulación, así que sin esto reutilizar
     * una clave devolvería el registro equivocado con un 201.
     */
    String discriminante();

    /**
     * Un indicador omitido vale «N»: lo dice el propio diseño de registro de la AEAT, «si no se
     * informa este campo se entenderá que tiene valor "N"». Vive aquí porque lo necesitan las dos
     * peticiones y es la regla del contrato, no un detalle de ninguna de ellas.
     */
    static boolean si(Boolean indicador) {
        return Boolean.TRUE.equals(indicador);
    }
}
