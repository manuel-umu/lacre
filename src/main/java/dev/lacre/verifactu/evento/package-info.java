/**
 * Lo que {@code verifactu} anuncia al resto de la aplicación cuando pasa algo digno de contarse.
 * <p>
 * Es una interfaz publicada aparte del modelo a propósito: {@code registro} es el modelo fiscal
 * que viaja a Maven Central, mientras que estos eventos hablan de filas ya escritas, con
 * identidades que asigna la persistencia. El núcleo no los publica —lo hace su adaptador— y
 * <strong>no sabe quién escucha</strong>, que es lo que permite extraerlo como librería.
 */
@org.springframework.modulith.NamedInterface("evento")
package dev.lacre.verifactu.evento;
