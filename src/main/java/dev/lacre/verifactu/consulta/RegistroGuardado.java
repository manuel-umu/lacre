package dev.lacre.verifactu.consulta;

import dev.lacre.shared.Huella;
import dev.lacre.verifactu.registro.IdFactura;
import dev.lacre.verifactu.registro.TipoRegistro;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Registro de la cadena visto desde fuera del módulo, sin el XML.
 *
 * @param huellaAnterior nula en el primer registro de la cadena
 * @param fechaHoraHusoGenRegistro con el huso original con el que se calculó la huella
 */
public record RegistroGuardado(
        UUID id,
        UUID obligadoId,
        long posicion,
        TipoRegistro tipo,
        IdFactura idFactura,
        Huella huella,
        Huella huellaAnterior,
        OffsetDateTime fechaHoraHusoGenRegistro) {}
