package dev.lacre.sif;

import dev.lacre.shared.Importe;
import dev.lacre.shared.Nif;
import dev.lacre.shared.ValorInvalidoException;

import java.time.LocalDate;
import java.util.List;

/**
 * Contenido de un registro de facturación de alta.
 * <p>
 * Recoge únicamente los campos que el RD 1007/2023, art. 10, fija a nivel legal. El
 * conjunto completo lo define el esquema {@code SuministroInformacion.xsd} de la AEAT,
 * que todavía no está en el repositorio.
 * <p>
 * El orden en que se declaran los componentes <strong>no tiene significado normativo</strong>:
 * el orden que importa es el de la concatenación para la huella, y ese vive en
 * {@link Canonicalizador}, sin implementar hasta disponer de la especificación.
 *
 * @implNote TODO Completar con los campos del XSD cuando esté en {@code src/main/resources/xsd/aeat/}:
 * {@code Desglose}, {@code SistemaInformatico}, {@code Subsanacion}, {@code RechazoPrevio},
 * {@code Macrodato}, {@code Tercero}, {@code Cupon}, {@code TipoRectificativa},
 * {@code FacturasRectificadas} y el resto.
 */
public record DatosRegistroAlta(
        Nif emisor,
        String numSerieFactura,
        LocalDate fechaExpedicion,
        String tipoFactura,
        String descripcionOperacion,
        List<Destinatario> destinatarios,
        Importe cuotaTotal,
        Importe importeTotal) {

    public DatosRegistroAlta {
        exigirPresente(emisor, "El emisor");
        exigirTexto(numSerieFactura, "El número de serie de la factura");
        exigirPresente(fechaExpedicion, "La fecha de expedición");
        exigirTexto(tipoFactura, "El tipo de factura");
        exigirTexto(descripcionOperacion, "La descripción de la operación");
        exigirPresente(destinatarios, "La lista de destinatarios");
        exigirPresente(cuotaTotal, "La cuota total");
        exigirPresente(importeTotal, "El importe total");
        destinatarios = List.copyOf(destinatarios);
    }

    /**
     * Una factura simplificada no lleva destinatario, de ahí que la lista pueda estar vacía.
     */
    public record Destinatario(Nif nif, String nombreRazon) {

        public Destinatario {
            exigirPresente(nif, "El NIF del destinatario");
            exigirTexto(nombreRazon, "El nombre o razón social del destinatario");
        }
    }

    private static void exigirPresente(Object valor, String campo) {
        if (valor == null) {
            throw new ValorInvalidoException(campo + " es obligatorio");
        }
    }

    private static void exigirTexto(String valor, String campo) {
        if (valor == null || valor.isBlank()) {
            throw new ValorInvalidoException(campo + " es obligatorio");
        }
    }
}
