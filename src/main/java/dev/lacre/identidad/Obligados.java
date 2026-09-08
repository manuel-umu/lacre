package dev.lacre.identidad;

import dev.lacre.shared.Nif;
import org.springframework.data.repository.CrudRepository;

import java.util.Optional;
import java.util.UUID;

/**
 * API pública del módulo: cargar y guardar obligados. Es lo único que el resto de la aplicación
 * necesita de {@code identidad} hoy.
 */
public interface Obligados extends CrudRepository<ObligadoTributario, UUID> {

    Optional<ObligadoTributario> findByNif(Nif nif);
}
