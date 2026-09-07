package dev.lacre.sif.internal.adaptador;

import org.springframework.data.repository.CrudRepository;

import java.util.UUID;

/**
 * Solo carga y guarda el agregado. Todo el lado de lectura —listados, filtros, la posición de
 * la cadena— va con {@code JdbcClient} y SQL explícito, según la regla de CQRS deliberado de
 * {@code CLAUDE.md}.
 * <p>
 * Es interno al módulo por partida doble: vive en {@code adaptador} y {@code ArquitecturaTest}
 * impide que nadie de fuera de {@code sif} lo toque.
 */
interface RegistroRepository extends CrudRepository<RegistroFacturacion, UUID> {
}
