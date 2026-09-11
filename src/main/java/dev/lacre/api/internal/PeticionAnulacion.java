package dev.lacre.api.internal;

import dev.lacre.verifactu.registro.GeneradoPor;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

/**
 * Anulación de una factura ya expedida.
 * <p>
 * Mucho más pequeña que el alta: una anulación no lleva desglose ni importes, solo dice qué
 * factura se anula y quién lo hace.
 * <p>
 * <strong>No anula un registro, anula una factura.</strong> El registro de anulación es un
 * eslabón <em>nuevo</em> de la cadena, no un borrado del anterior: la cadena es de solo
 * inserción y nada de lo que entra en ella sale.
 * <p>
 * El envoltorio se llama {@code idFacturaAnulada} para que no haya duda de a qué factura se
 * refiere, pero dentro reutiliza los nombres de campo del alta. El diseño oficial los llama
 * ahí {@code IDEmisorFacturaAnulada}, {@code NumSerieFacturaAnulada} y
 * {@code FechaExpedicionFacturaAnulada}; repetir «Anulada» en los dos niveles daría un JSON
 * tartamudo sin aclarar nada que el nombre del envoltorio y la ruta no digan ya.
 *
 * @param rechazoPrevio aquí es un booleano y no el catálogo de tres valores del alta: el esquema
 *                      solo admite {@code S} o {@code N} para las anulaciones
 */
public record PeticionAnulacion(
        @NotNull @Valid IdFacturaDto idFacturaAnulada,
        String refExterna,
        Boolean sinRegistroPrevio,
        Boolean rechazoPrevio,
        GeneradoPor generadoPor,
        @Valid PersonaDto generador) implements PeticionRegistro {

    @Override
    public IdFacturaDto factura() {
        return idFacturaAnulada;
    }
}
