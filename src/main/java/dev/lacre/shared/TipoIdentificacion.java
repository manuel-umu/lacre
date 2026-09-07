package dev.lacre.shared;

/**
 * Tipo de identificador distinto del NIF, lista L7 del anexo de la Orden HAC/1177/2024.
 * <p>
 * El catálogo empieza en {@code 02}: el {@code 01} (NIF de contraparte) no es válido aquí,
 * porque para ese caso el esquema exige usar el elemento {@code NIF} y no {@code IDOtro}.
 */
public enum TipoIdentificacion {

    /** NIF-IVA. */
    NIF_IVA("02"),

    /** Pasaporte. */
    PASAPORTE("03"),

    /** Documento oficial de identificación expedido por el país de residencia. */
    ID_PAIS_RESIDENCIA("04"),

    /** Certificado de residencia. */
    CERTIFICADO_RESIDENCIA("05"),

    /** Otro documento probatorio. */
    OTRO_DOCUMENTO("06"),

    /** No censado. */
    NO_CENSADO("07");

    private final String codigo;

    TipoIdentificacion(String codigo) {
        this.codigo = codigo;
    }

    public String codigo() {
        return codigo;
    }
}
