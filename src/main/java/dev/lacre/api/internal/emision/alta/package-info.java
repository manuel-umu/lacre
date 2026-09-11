/**
 * El alta: el contrato de {@code POST /v1/registros/alta} y su traducción al modelo fiscal.
 * <p>
 * Está separado de {@code anulacion} porque son veinticinco campos, un desglose con sus tres
 * catálogos y las reglas que los relacionan, frente a los seis campos de una anulación. Lo que
 * comparten —la identificación de la factura, las personas, la respuesta, el caso de uso— vive
 * en el paquete de arriba.
 */
package dev.lacre.api.internal.emision.alta;
