/**
 * Lo que se pregunta sobre lo ya guardado: en qué quedó un registro y si la cadena de un
 * obligado sigue encajando.
 * <p>
 * Espejo de {@code verifactu.consulta}, con una diferencia que justifica que exista este paquete
 * y no solo aquel: el estado de un registro <strong>cruza dos módulos</strong> —el eslabón es de
 * {@code verifactu} y el desenlace de la remisión es de {@code remision}—, y componerlos solo
 * puede hacerse aquí, que es el único sitio que ve a los dos.
 * <p>
 * Separado de {@code emision} porque leer no arrastra nada de lo que arrastra escribir: ni
 * transacción, ni cerrojo, ni idempotencia.
 */
package dev.lacre.api.internal.consulta;
