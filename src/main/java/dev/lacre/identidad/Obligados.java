package dev.lacre.identidad;

import dev.lacre.shared.Nif;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.repository.CrudRepository;

/** Repositorio de obligados tributarios. */
public interface Obligados extends CrudRepository<ObligadoTributario, UUID> {

    Optional<ObligadoTributario> findByNif(Nif nif);
}
