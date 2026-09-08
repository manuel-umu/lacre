package dev.lacre.verifactu.evento;

import java.util.UUID;

/**
 * Se ha añadido un registro de facturación a la cadena de un obligado.
 * <p>
 * Lo consume {@code remision} con un oyente <strong>síncrono</strong>, dentro de la misma
 * transacción: la fila del outbox es parte del mismo hecho que el registro, y una ventana entre
 * ambos dejaría un registro que nunca llegaría a la AEAT. Ver el
 * <a href="../../../../../docs/adr/0004-eventos-de-dominio-sincronos.md">ADR 0004</a>.
 * <p>
 * Lleva identificadores y no el registro entero: quien lo escuche puede leerlo si lo necesita, y
 * así el evento no arrastra el agregado de otro módulo.
 */
public record RegistroCreado(UUID registroId, UUID obligadoId) {
}
