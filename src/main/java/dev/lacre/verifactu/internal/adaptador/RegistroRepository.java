package dev.lacre.verifactu.internal.adaptador;

import java.util.UUID;
import org.springframework.data.repository.CrudRepository;

/** Carga y guarda el agregado. El lado de lectura va con {@code JdbcClient} y SQL explícito. */
interface RegistroRepository extends CrudRepository<RegistroFacturacion, UUID> {}
