/**
 * Implementación de la API REST, repartida por concepto: {@code emision} para lo que se escribe,
 * {@code consulta} para lo que se lee y {@code autenticacion} para quién puede hacerlo.
 * <p>
 * En la raíz queda solo {@code ManejadorDeErrores}, y es a propósito: traduce a
 * {@code ProblemDetail} lo que lanzan los tres, más lo que lanzan el dominio e {@code identidad},
 * así que no es de ninguno. Ponerlo dentro de uno lo haría parecer suyo.
 */
package dev.lacre.api.internal;
