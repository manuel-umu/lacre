package dev.lacre.verifactu.consulta;

import dev.lacre.verifactu.registro.IdFactura;
import dev.lacre.verifactu.registro.TipoRegistro;
import java.util.UUID;

/**
 * Registro guardado con lo necesario para remitirlo y reconocer su respuesta.
 *
 * @param xml la serialización tal y como se guardó
 * @param idFactura junto con {@code tipo}, identifica la línea de respuesta de la AEAT
 */
public record RegistroRemitible(UUID id, IdFactura idFactura, TipoRegistro tipo, String xml) {}
