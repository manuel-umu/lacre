package dev.lacre.remision;

import org.springframework.data.repository.CrudRepository;

import java.util.Optional;
import java.util.UUID;

/**
 * Carga y guarda envíos. El lado de lectura del despachador —lotes con
 * {@code for update skip locked}— irá con {@code JdbcClient} en la Fase 6.3, no por aquí.
 */
public interface Envios extends CrudRepository<EnvioRegistro, UUID> {

    Optional<EnvioRegistro> findByRegistroId(UUID registroId);
}
