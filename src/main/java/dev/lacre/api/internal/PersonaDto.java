package dev.lacre.api.internal;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

/**
 * Persona física o jurídica: destinatario, tercero o productor.
 * <p>
 * El XSD modela la identificación como un {@code choice} entre {@code NIF} e {@code IDOtro}, y
 * aquí se conserva: hay que aportar <strong>uno de los dos</strong>. Un extranjero sin NIF
 * español se identifica con {@code idOtro}. Que sea exactamente uno lo comprueba el mapeador,
 * no una anotación: el mensaje que sale de ahí explica la regla, y un
 * {@code @AssertTrue} anónimo no.
 */
public record PersonaDto(
        @NotBlank String nombreRazon,
        String nif,
        @Valid IdOtroDto idOtro) {

    /**
     * @param tipo código de la lista L7: 02 NIF-IVA, 03 pasaporte, 04 documento del país de
     *             residencia, 05 certificado de residencia, 06 otro documento, 07 no censado
     */
    public record IdOtroDto(String codigoPais, @NotBlank String tipo, @NotBlank String id) {
    }
}
