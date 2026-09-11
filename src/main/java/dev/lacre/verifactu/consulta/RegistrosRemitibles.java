package dev.lacre.verifactu.consulta;

import java.util.List;
import java.util.UUID;

/** Entrega los registros guardados que hay que remitir. */
public interface RegistrosRemitibles {

    /** En el mismo orden en que se piden; sin entrada para los que no existan. */
    List<RegistroRemitible> de(List<UUID> registroIds);
}
