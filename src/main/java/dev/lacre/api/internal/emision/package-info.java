/**
 * Lo que el ERP pide cuando pasa algo: expedir una factura o anularla.
 * <p>
 * Agrupado por concepto y no por tipo de clase, como manda {@code CLAUDE.md}: aquí conviven el
 * contrato ({@code PeticionAlta}, {@code PeticionAnulacion}), su traducción al modelo fiscal
 * ({@code Mapeador}), el caso de uso ({@code Emisiones}) y el controlador. Repartirlos en
 * {@code dto/}, {@code servicios/} y {@code controller/} obligaría a abrir tres paquetes para
 * seguir una sola petición.
 * <p>
 * Es el espejo de {@code verifactu.emision}, que es el puerto al que llama: los dos hablan de
 * escribir en la cadena, con la diferencia de que este habla el contrato que se vende y aquel el
 * modelo fiscal que no se mueve cuando el contrato cambia.
 */
package dev.lacre.api.internal.emision;
