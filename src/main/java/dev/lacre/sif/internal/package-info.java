/**
 * Implementación del núcleo: la canonicalización conforme a la AEAT y el cálculo SHA-256.
 * <p>
 * Interno para el resto de módulos de la aplicación, pero <strong>sí forma parte de la
 * librería publicable</strong>. La frontera de extracción es el subpaquete
 * {@code adaptador}, que es el único sitio donde puede aparecer Spring.
 */
package dev.lacre.sif.internal;
