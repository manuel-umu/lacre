package dev.lacre.verifactu.emision;

import dev.lacre.verifactu.registro.DatosRegistro;

import java.util.UUID;

/**
 * Añade un registro a la cadena de un obligado: el único caso de uso de escritura que
 * {@code verifactu} publica.
 * <p>
 * Existe porque la implementación —{@code CadenaDeRegistros}— vive en el adaptador, que es
 * interno por definición: necesita Spring, transacciones y el cerrojo consultivo de Postgres, y
 * nada de eso puede asomar en la librería que se publica en Maven Central. Sin este puerto, el
 * módulo {@code api} tendría que entrar en {@code verifactu.internal.adaptador}, que es justo lo
 * que prohíbe {@code ArquitecturaTest}.
 * <p>
 * Es el mismo patrón que {@code consulta.RegistrosRemitibles} para el lado de lectura.
 */
public interface EmisorDeRegistros {

    /**
     * @throws dev.lacre.shared.ValorInvalidoException si los datos no forman un registro válido
     */
    RegistroEmitido emitir(UUID obligadoId, DatosRegistro datos);
}
