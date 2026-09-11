package dev.lacre.verifactu.emision;

import dev.lacre.verifactu.registro.DatosRegistro;

import java.util.UUID;

/** Puerto de escritura de {@code verifactu}: añade un registro a la cadena de un obligado. */
public interface EmisorDeRegistros {

    /**
     * @throws dev.lacre.shared.ValorInvalidoException si los datos no forman un registro válido
     */
    RegistroEmitido emitir(UUID obligadoId, DatosRegistro datos);
}
