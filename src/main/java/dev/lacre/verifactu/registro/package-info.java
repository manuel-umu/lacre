/**
 * Modelo del registro de facturación de alta: sus datos, su identificación, quién lo emite y
 * con qué sistema, y las claves cerradas que solo aparecen aquí.
 * <p>
 * Es API pública del módulo {@code verifactu} y parte de la librería publicable, así que ninguna
 * clase de este paquete puede depender de Spring ni de una base de datos.
 *
 * @see dev.lacre.verifactu.registro.DatosRegistroAlta
 */
@org.springframework.modulith.NamedInterface("registro")
package dev.lacre.verifactu.registro;
