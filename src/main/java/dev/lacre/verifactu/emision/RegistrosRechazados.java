package dev.lacre.verifactu.emision;

import java.util.Collection;
import java.util.Set;
import java.util.UUID;

/** Puerto hacia quien conoce el desenlace de la remisión: qué registros rechazó la AEAT. */
public interface RegistrosRechazados {

    /** De los registros indicados, los que la AEAT rechazó. */
    Set<UUID> entre(Collection<UUID> registroIds);
}
