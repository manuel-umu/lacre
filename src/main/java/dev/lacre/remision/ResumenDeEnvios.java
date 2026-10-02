package dev.lacre.remision;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Lado de lectura del outbox: cómo van los envíos de cada obligado. */
public interface ResumenDeEnvios {

    /** Los envíos de cada obligado que tiene alguno, por su identificador. */
    Map<UUID, EnviosDeObligado> porObligado();

    /** Los pendientes del obligado, del más antiguo al más reciente. */
    List<EnvioRegistro> pendientesDe(UUID obligadoId, int maximo);

    /** Los apartados del obligado, del más antiguo al más reciente. */
    List<EnvioRegistro> apartadosDe(UUID obligadoId, int maximo);

    /** Los rechazados y aceptados con errores del obligado, del más reciente al más antiguo. */
    List<EnvioRegistro> conErroresDe(UUID obligadoId, int maximo);
}
