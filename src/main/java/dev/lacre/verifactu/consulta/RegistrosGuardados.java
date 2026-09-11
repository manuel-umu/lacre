package dev.lacre.verifactu.consulta;

import java.util.Optional;
import java.util.UUID;

/**
 * Consulta de lo que ya está en la cadena: un registro suelto, o la cadena entera de un obligado.
 * <p>
 * Existe por lo mismo que {@link RegistrosRemitibles}: para que ningún otro módulo escriba SQL
 * contra {@code registro_facturacion}. Ese acoplamiento iría por el nombre de una tabla, no por
 * un {@code import}, y ninguna regla ArchUnit lo vería.
 */
public interface RegistrosGuardados {

    Optional<RegistroGuardado> porId(UUID registroId);

    /**
     * Recorre la cadena entera del obligado. Un obligado sin registros da una verificación de
     * cero eslabones y sin roturas, que es lo correcto: una cadena vacía está íntegra.
     */
    VerificacionDeCadena verificarCadenaDe(UUID obligadoId);
}
