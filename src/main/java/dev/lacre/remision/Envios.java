package dev.lacre.remision;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.repository.CrudRepository;

/** Repositorio de envíos. */
public interface Envios extends CrudRepository<EnvioRegistro, UUID> {

    Optional<EnvioRegistro> findByRegistroId(UUID registroId);
}
