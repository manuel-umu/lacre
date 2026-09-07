package dev.lacre.shared;

/**
 * Forma de identificar fiscalmente a una persona física o jurídica ante la AEAT.
 * <p>
 * El XSD lo modela como un {@code choice} excluyente entre {@code NIF} e {@code IDOtro}: o se
 * aporta un NIF español, o un identificador extranjero, nunca ambos. Aquí eso es un tipo
 * sellado, de modo que no se pueda construir una persona sin identificar ni con los dos.
 */
public sealed interface IdentificadorFiscal permits Nif, IdOtro {
}
