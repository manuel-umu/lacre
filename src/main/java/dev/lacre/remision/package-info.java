/**
 * El envío de los registros de facturación a la AEAT: el outbox, su estado y —a partir de la
 * Fase 6— el cliente que los remite.
 * <p>
 * Escucha a {@code verifactu} y no al revés. La fila del outbox se crea en la misma transacción
 * que el registro; el despacho es asíncrono y reintentable, porque la norma exige el registro,
 * no que la AEAT esté disponible. Ver el
 * <a href="../../../../docs/adr/0004-eventos-de-dominio-sincronos.md">ADR 0004</a>.
 * <p>
 * Como {@code identidad}, este módulo <strong>no se publica</strong>, así que su agregado lleva
 * las anotaciones de Spring Data JDBC en el paquete raíz en vez de duplicarse en un adaptador.
 */
package dev.lacre.remision;
