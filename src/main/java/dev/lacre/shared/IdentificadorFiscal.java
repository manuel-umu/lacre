package dev.lacre.shared;

/**
 * Identificación fiscal de una persona ante la AEAT: un NIF español o un identificador
 * extranjero, nunca ambos.
 */
public sealed interface IdentificadorFiscal permits Nif, IdOtro {
}
