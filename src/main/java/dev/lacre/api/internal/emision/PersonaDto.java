package dev.lacre.api.internal.emision;

import dev.lacre.shared.IdOtro;
import dev.lacre.shared.IdentificadorFiscal;
import dev.lacre.shared.Nif;
import dev.lacre.shared.TipoIdentificacion;
import dev.lacre.shared.ValorInvalidoException;
import dev.lacre.verifactu.registro.PersonaFisicaJuridica;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Persona física o jurídica: destinatario del alta, o generador de la anulación. Por eso vive en
 * la raíz de {@code emision} y no en ninguno de los dos subpaquetes.
 * <p>
 * El XSD modela la identificación como un {@code choice} entre {@code NIF} e {@code IDOtro}, y
 * aquí se conserva: hay que aportar <strong>uno de los dos</strong>. Un extranjero sin NIF
 * español se identifica con {@code idOtro}. Que sea exactamente uno lo comprueba
 * {@link #aDominio()} y no una anotación: el mensaje que sale de ahí explica la regla, y un
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

    private static final Map<String, TipoIdentificacion> POR_CODIGO = Arrays
            .stream(TipoIdentificacion.values())
            .collect(Collectors.toUnmodifiableMap(TipoIdentificacion::codigo, tipo -> tipo));

    /**
     * Admite nulo y devuelve nulo: el tercero del alta y el generador de la anulación son
     * opcionales. Nombre distinto del de abajo y no una sobrecarga: con el mismo nombre, un
     * {@code PersonaDto::aDominio} en un {@code stream} queda ambiguo y no compila.
     */
    public static PersonaFisicaJuridica opcional(PersonaDto dto) {
        return dto == null ? null : dto.aDominio();
    }

    public PersonaFisicaJuridica aDominio() {
        return new PersonaFisicaJuridica(nombreRazon, identificador());
    }

    private IdentificadorFiscal identificador() {
        boolean tieneNif = nif != null && !nif.isBlank();
        if (tieneNif == (idOtro != null)) {
            throw new ValorInvalidoException(
                    "El destinatario o tercero " + nombreRazon + " debe identificarse con "
                            + "nif o con idOtro, exactamente uno de los dos: el XSD los declara "
                            + "excluyentes");
        }
        if (tieneNif) {
            return new Nif(nif);
        }
        TipoIdentificacion tipo = POR_CODIGO.get(idOtro.tipo() == null ? null : idOtro.tipo().strip());
        if (tipo == null) {
            throw new ValorInvalidoException("Código de tipo de identificación desconocido: "
                    + idOtro.tipo() + ". Admitidos: " + POR_CODIGO.keySet().stream().sorted().toList());
        }
        return new IdOtro(idOtro.codigoPais(), tipo, idOtro.id());
    }
}
