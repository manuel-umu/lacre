package dev.lacre.remision;

import org.springframework.data.repository.CrudRepository;

import java.util.Optional;
import java.util.UUID;

/** Repositorio de envíos. */
public interface Envios extends CrudRepository<EnvioRegistro, UUID> {

    Optional<EnvioRegistro> findByRegistroId(UUID registroId);
}
