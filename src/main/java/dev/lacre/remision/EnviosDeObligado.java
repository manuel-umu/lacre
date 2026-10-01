package dev.lacre.remision;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Envíos de un obligado contados por estado, con la fecha de creación del pendiente más antiguo, o
 * {@code null} si no tiene pendientes.
 */
public record EnviosDeObligado(
        UUID obligadoId,
        long pendientes,
        long aceptados,
        long aceptadosConErrores,
        long rechazados,
        long duplicados,
        OffsetDateTime pendienteMasAntiguo) {}
