package dev.lacre.verifactu.consulta;

import java.util.List;
import java.util.UUID;

/**
 * Entrega los registros guardados que hay que remitir.
 * <p>
 * Existe para que nadie lea la tabla del núcleo por su cuenta: el acoplamiento por el nombre de
 * una tabla no lo detecta ninguna regla ArchUnit, porque no va por un {@code import}, y sería
 * igual de real.
 */
public interface RegistrosRemitibles {

    /** En el mismo orden en que se piden; sin entrada para los que no existan. */
    List<RegistroRemitible> de(List<UUID> registroIds);
}
