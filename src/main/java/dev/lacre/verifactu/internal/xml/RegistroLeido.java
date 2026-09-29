package dev.lacre.verifactu.internal.xml;

import dev.lacre.shared.Huella;
import dev.lacre.verifactu.huella.Canonicalizador;
import dev.lacre.verifactu.internal.CalculadorHuella;
import dev.lacre.verifactu.registro.CamposDeHuella;
import dev.lacre.verifactu.registro.RegistroAnterior;
import java.time.OffsetDateTime;
import java.util.Optional;

/**
 * Lo que se lee de un registro guardado para comprobar su huella.
 *
 * @param huella la que declara el propio XML
 */
public record RegistroLeido(
        CamposDeHuella campos,
        Optional<RegistroAnterior> anterior,
        OffsetDateTime fechaHoraHusoGenRegistro,
        Huella huella) {

    /** La huella que corresponde al contenido leído, calculada de nuevo. */
    public Huella huellaRecalculada(Canonicalizador canonicalizador) {
        return CalculadorHuella.calcular(canonicalizador.canonicalizar(campos, anterior, fechaHoraHusoGenRegistro));
    }
}
