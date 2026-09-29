package dev.lacre.verifactu.evento;

import java.util.UUID;

/**
 * Se ha añadido un registro de facturación a la cadena de un obligado. Sus oyentes corren de
 * forma síncrona, dentro de la misma transacción.
 */
public record RegistroCreado(UUID registroId, UUID obligadoId) {}
