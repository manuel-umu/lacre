/**
 * Value objects que comparten todos los módulos: identificadores fiscales, importes,
 * porcentajes y huellas.
 * <p>
 * No es un módulo de negocio sino el suelo común, así que se declara como módulo compartido en
 * {@code LacreApplication} y cualquiera puede depender de él sin declararlo. La dependencia va
 * en un solo sentido: {@code shared} no conoce a ningún módulo, y lo vigila
 * {@code ArquitecturaTest}.
 * <p>
 * Viaja con la librería que se publica en Maven Central, así que no puede depender de Spring.
 * Por eso los conversores de Spring Data JDBC de estos tipos viven en {@code dev.lacre} y no
 * aquí.
 */
package dev.lacre.shared;
