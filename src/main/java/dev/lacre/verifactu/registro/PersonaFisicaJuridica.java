package dev.lacre.verifactu.registro;

import static dev.lacre.shared.ReglaAeatIncumplidaException.exigir;
import static dev.lacre.shared.TipoIdentificacion.NO_CENSADO;
import static dev.lacre.shared.TipoIdentificacion.PASAPORTE;

import dev.lacre.shared.IdOtro;
import dev.lacre.shared.IdentificadorFiscal;
import dev.lacre.shared.Nif;
import dev.lacre.shared.ValorInvalidoException;

/**
 * Persona física o jurídica, española o extranjera, {@code PersonaFisicaJuridicaType} del XSD.
 * Sirve para destinatarios, terceros y el titular del sistema informático.
 */
public record PersonaFisicaJuridica(String nombreRazon, IdentificadorFiscal identificador) {

    public static final int MAXIMO_LONGITUD_NOMBRE = 120;

    public PersonaFisicaJuridica {
        if (nombreRazon == null || nombreRazon.isBlank()) {
            throw new ValorInvalidoException("El nombre o razón social es obligatorio");
        }
        nombreRazon = nombreRazon.strip();
        if (nombreRazon.length() > MAXIMO_LONGITUD_NOMBRE) {
            throw new ValorInvalidoException("El nombre o razón social admite como máximo " + MAXIMO_LONGITUD_NOMBRE
                    + " caracteres y tiene " + nombreRazon.length());
        }
        if (identificador == null) {
            throw new ValorInvalidoException("El identificador fiscal es obligatorio");
        }
    }

    /** Como destinatario, un identificador extranjero de España solo es pasaporte o no censado. */
    void exigirComoDestinatario() {
        if (identificador instanceof IdOtro otro && "ES".equals(otro.codigoPais())) {
            exigir(
                    otro.tipo() == PASAPORTE || otro.tipo() == NO_CENSADO,
                    "1126",
                    "Un destinatario con código de país ES se identifica con pasaporte (03) o "
                            + "como no censado (07), y es " + otro.tipo().codigo());
        }
    }

    /** Como tercero: ni no censado, ni el obligado, y de España solo con pasaporte. */
    void exigirComoTercero(Nif obligado) {
        switch (identificador) {
            case Nif nif ->
                exigir(
                        !nif.equals(obligado),
                        "1188",
                        "El NIF del tercero debe ser distinto del obligado, " + obligado.valor());
            case IdOtro otro -> {
                exigir(otro.tipo() != NO_CENSADO, "1211", "El tercero no puede identificarse como no censado (07)");
                exigirPasaporteSiEsDeEspana(otro, "el tercero");
            }
        }
    }

    /** Como generador de una anulación, según quién la genere. */
    void exigirComoGenerador(GeneradoPor generadoPor) {
        switch (generadoPor) {
            case E ->
                exigir(
                        identificador instanceof Nif,
                        "1227",
                        "Si la anulación la genera el expedidor (E), el generador se identifica con " + "NIF");
            case D -> {
                if (identificador instanceof IdOtro otro && "ES".equals(otro.codigoPais())) {
                    exigir(
                            otro.tipo() == PASAPORTE || otro.tipo() == NO_CENSADO,
                            "1230",
                            "Un generador destinatario con código de país ES se identifica con "
                                    + "pasaporte (03) o como no censado (07), y es "
                                    + otro.tipo().codigo());
                }
            }
            case T -> {
                if (identificador instanceof IdOtro otro) {
                    exigir(
                            otro.tipo() != NO_CENSADO,
                            "1229",
                            "Un generador tercero no puede identificarse como no censado (07)");
                    exigirPasaporteSiEsDeEspana(otro, "el generador");
                }
            }
        }
    }

    /** Como productor del sistema informático: ni no censado, y de España solo con pasaporte. */
    void exigirComoProductor() {
        if (identificador instanceof IdOtro otro) {
            exigir(
                    otro.tipo() != NO_CENSADO,
                    "1221",
                    "El productor del sistema informático no puede identificarse como no " + "censado (07)");
            exigirPasaporteSiEsDeEspana(otro, "el productor del sistema informático");
        }
    }

    private static void exigirPasaporteSiEsDeEspana(IdOtro otro, String quien) {
        if ("ES".equals(otro.codigoPais())) {
            exigir(
                    otro.tipo() == PASAPORTE,
                    "1232",
                    "Con código de país ES, " + quien + " se identifica con pasaporte (03), y es "
                            + otro.tipo().codigo());
        }
    }
}
