package dev.lacre.remision;

import java.util.Map;
import java.util.UUID;

/** Lado de lectura del outbox: cómo van los envíos de cada obligado. */
public interface ResumenDeEnvios {

    /** Los envíos de cada obligado que tiene alguno, por su identificador. */
    Map<UUID, EnviosDeObligado> porObligado();
}
