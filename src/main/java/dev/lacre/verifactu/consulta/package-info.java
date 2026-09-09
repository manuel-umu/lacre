/**
 * Lo que {@code verifactu} deja consultar de los registros que ya ha guardado.
 * <p>
 * Existe para que nadie lea su tabla por su cuenta. La alternativa —que {@code remision} hiciera
 * su propio SQL contra {@code registro_facturacion}— no la detectaría ninguna regla ArchUnit,
 * porque el acoplamiento iría por el nombre de una tabla y no por un {@code import}, y sería
 * igual de real: cambiar el esquema del núcleo rompería a otro módulo en silencio.
 * <p>
 * Es interfaz publicada aparte de {@code registro}, que es el modelo fiscal que viaja a Maven
 * Central, por la misma razón que {@code evento}: esto habla de filas guardadas.
 */
@org.springframework.modulith.NamedInterface("consulta")
package dev.lacre.verifactu.consulta;
