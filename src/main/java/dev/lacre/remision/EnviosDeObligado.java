package dev.lacre.remision;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Envíos de un obligado contados por estado, con la fecha de creación del pendiente más antiguo, o
 * {@code null} si no tiene pendientes.
 *
 * @param erroresAtendidos rechazados y aceptados con errores que el operador ya dio por atendidos;
 *                         también cuentan en su estado
 */
public record EnviosDeObligado(
        UUID obligadoId,
        long pendientes,
        long aceptados,
        long aceptadosConErrores,
        long rechazados,
        long duplicados,
        long apartados,
        long erroresAtendidos,
        OffsetDateTime pendienteMasAntiguo) {}
