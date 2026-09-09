package dev.lacre.verifactu.consulta;

import dev.lacre.verifactu.registro.IdFactura;
import dev.lacre.verifactu.registro.TipoRegistro;

import java.util.UUID;

/**
 * Un registro ya guardado, con lo justo para remitirlo y para reconocer su respuesta.
 *
 * @param xml la serialización <strong>tal y como se guardó</strong>. No se vuelve a generar: la
 *            huella se calculó sobre unos valores concretos, y este XML es la única prueba de qué
 *            se firmó.
 * @param idFactura y {@code tipo} identifican la línea que devolverá la AEAT. Hacen falta los
 *            dos: en un mismo lote pueden ir el alta y la anulación de la misma factura.
 */
public record RegistroRemitible(UUID id, IdFactura idFactura, TipoRegistro tipo, String xml) {
}
