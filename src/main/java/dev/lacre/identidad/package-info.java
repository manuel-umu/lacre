/**
 * El obligado tributario por cuya cuenta se expiden las facturas: quién es y con qué zona
 * horaria se fechan sus registros.
 * <p>
 * Multi-obligado es requisito del producto y no una extensión: un ERP da servicio a cientos de
 * obligados desde una sola instalación de lacre (ADR 0005).
 * <p>
 * <strong>Este módulo no se publica en Maven Central</strong> —solo {@code verifactu} y
 * {@code shared}—, así que su agregado lleva las anotaciones de Spring Data JDBC en el paquete
 * raíz en vez de duplicarse en un adaptador. Ver el refinamiento del
 * <a href="../../../../docs/adr/0002-arquitectura-hexagonal-por-modulo.md">ADR 0002</a>.
 */
package dev.lacre.identidad;
