package dev.lacre.verifactu.consulta;

import dev.lacre.shared.Huella;
import dev.lacre.verifactu.registro.IdFactura;
import dev.lacre.verifactu.registro.TipoRegistro;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Un registro de la cadena, visto desde fuera del módulo.
 * <p>
 * No trae el XML, al contrario que {@link RegistroRemitible}: quien consulta el estado de una
 * factura quiere saber dónde quedó y con qué huella, no llevarse el documento entero.
 *
 * @param huellaAnterior nula solo en el primer registro de la cadena
 * @param fechaHoraHusoGenRegistro con el huso con el que se calculó la huella, no con el de la
 *                                 sesión de base de datos
 */
public record RegistroGuardado(
        UUID id,
        UUID obligadoId,
        long posicion,
        TipoRegistro tipo,
        IdFactura idFactura,
        Huella huella,
        Huella huellaAnterior,
        OffsetDateTime fechaHoraHusoGenRegistro) {
}
