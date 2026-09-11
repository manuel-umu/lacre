/**
 * La anulación: el contrato de {@code POST /v1/registros/anulacion}.
 * <p>
 * Una sola clase, y es honesto que así sea: una anulación no lleva desglose ni importes, solo
 * dice qué factura se anula y quién lo hace, de modo que el mapeo cabe dentro del propio record.
 * Inventarle un {@code MapeadorDeAnulacion} por simetría con {@code alta} sería un fichero que
 * solo existiría para que los dos paquetes se parecieran.
 */
package dev.lacre.api.internal.emision.anulacion;
