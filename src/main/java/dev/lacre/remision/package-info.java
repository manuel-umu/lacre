/**
 * Remisión de los registros de facturación a la AEAT: outbox, estados y cliente. La fila del
 * outbox se crea en la misma transacción que el registro; el despacho es asíncrono y
 * reintentable.
 */
package dev.lacre.remision;
