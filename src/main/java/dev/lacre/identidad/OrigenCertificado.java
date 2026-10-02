package dev.lacre.identidad;

/** De dónde sale el certificado con el que se remite por un obligado. */
public enum OrigenCertificado {

    /** El suyo, un {@code <NIF>.p12}. */
    PROPIO,

    /** El del presentador, que remite por los obligados sin certificado propio. */
    PRESENTADOR,

    /** Ninguno: sus registros no se pueden remitir. */
    NINGUNO
}
