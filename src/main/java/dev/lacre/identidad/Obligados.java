package dev.lacre.identidad;

import dev.lacre.shared.Nif;
import org.springframework.data.repository.CrudRepository;

import java.util.Optional;
import java.util.UUID;

/** Repositorio de obligados tributarios. */
public interface Obligados extends CrudRepository<ObligadoTributario, UUID> {

    Optional<ObligadoTributario> findByNif(Nif nif);
}
