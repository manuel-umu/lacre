package dev.lacre.api.internal.emision.alta;

import dev.lacre.api.internal.emision.IdFacturaDto;
import dev.lacre.api.internal.emision.PersonaDto;
import dev.lacre.api.internal.emision.PeticionRegistro;
import dev.lacre.shared.Importe;
import dev.lacre.shared.Porcentaje;
import dev.lacre.shared.ValorInvalidoException;
import dev.lacre.verifactu.desglose.Calificacion;
import dev.lacre.verifactu.desglose.CalificacionOperacion;
import dev.lacre.verifactu.desglose.ClaveRegimen;
import dev.lacre.verifactu.desglose.Desglose;
import dev.lacre.verifactu.desglose.DetalleDesglose;
import dev.lacre.verifactu.desglose.Impuesto;
import dev.lacre.verifactu.desglose.OperacionExenta;
import dev.lacre.verifactu.registro.DatosRegistroAlta;
import dev.lacre.verifactu.registro.IdFactura;
import dev.lacre.verifactu.registro.ImporteRectificacion;
import dev.lacre.verifactu.registro.PersonaFisicaJuridica;
import dev.lacre.verifactu.registro.SistemaInformatico;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Traduce el alta del contrato al modelo fiscal. A mano y sin generador: MapStruct está
 * prohibido en el proyecto, y de todas formas la mitad de esto no es copiar campos sino
 * resolver catálogos y tipos sellados.
 * <p>
 * <strong>No revalida lo que ya valida el dominio.</strong> Los constructores de {@code shared}
 * y de {@link DatosRegistroAlta} comprueban longitudes, rangos y reglas de la AEAT; repetirlo
 * aquí garantizaría que un día las dos versiones discrepen. Lo único que se comprueba en este
 * fichero es lo que el dominio no puede ver porque nace de la forma del JSON: que un código de
 * catálogo exista.
 * <p>
 * Estático y sin Spring, al contrario que el componente que era antes: el único dato que
 * necesitaba inyectado —el sistema informático— lo aporta ahora quien llama, porque es
 * {@code Emisiones} quien lo tiene.
 */
final class MapeadorDeAlta {

    private static final Map<String, Impuesto> IMPUESTOS_POR_CODIGO = Arrays
            .stream(Impuesto.values())
            .collect(Collectors.toUnmodifiableMap(Impuesto::codigo, impuesto -> impuesto));

    private MapeadorDeAlta() {
    }

    static DatosRegistroAlta aDatos(PeticionAlta peticion, SistemaInformatico sistemaInformatico) {
        return DatosRegistroAlta.builder()
                .idFactura(peticion.idFactura().aDominio())
                .refExterna(peticion.refExterna())
                .nombreRazonEmisor(peticion.nombreRazonEmisor())
                .subsanacion(PeticionRegistro.si(peticion.subsanacion()))
                .rechazoPrevio(peticion.rechazoPrevio())
                .tipoFactura(peticion.tipoFactura())
                .tipoRectificativa(peticion.tipoRectificativa())
                .facturasRectificadas(idsFactura(peticion.facturasRectificadas()))
                .facturasSustituidas(idsFactura(peticion.facturasSustituidas()))
                .importeRectificacion(rectificacion(peticion.importeRectificacion()))
                .fechaOperacion(peticion.fechaOperacion())
                .descripcionOperacion(peticion.descripcionOperacion())
                .facturaSimplificadaArt7273(PeticionRegistro.si(peticion.facturaSimplificadaArt7273()))
                .facturaSinIdentifDestinatarioArt61d(
                        PeticionRegistro.si(peticion.facturaSinIdentifDestinatarioArt61d()))
                .macrodato(PeticionRegistro.si(peticion.macrodato()))
                .emitidaPorTerceroODestinatario(peticion.emitidaPorTerceroODestinatario())
                .tercero(PersonaDto.opcional(peticion.tercero()))
                .destinatarios(personas(peticion.destinatarios()))
                .cupon(PeticionRegistro.si(peticion.cupon()))
                .desglose(new Desglose(peticion.desglose().stream().map(MapeadorDeAlta::detalle).toList()))
                .cuotaTotal(importe(peticion.cuotaTotal()))
                .importeTotal(importe(peticion.importeTotal()))
                .sistemaInformatico(sistemaInformatico)
                .numRegistroAcuerdoFacturacion(peticion.numRegistroAcuerdoFacturacion())
                .idAcuerdoSistemaInformatico(peticion.idAcuerdoSistemaInformatico())
                .build();
    }

    private static List<IdFactura> idsFactura(List<IdFacturaDto> dtos) {
        return dtos == null ? List.of() : dtos.stream().map(IdFacturaDto::aDominio).toList();
    }

    private static List<PersonaFisicaJuridica> personas(List<PersonaDto> dtos) {
        return dtos == null ? List.of() : dtos.stream().map(PersonaDto::aDominio).toList();
    }

    private static DetalleDesglose detalle(PeticionAlta.Detalle dto) {
        return new DetalleDesglose(
                dto.impuesto() == null ? null : impuesto(dto.impuesto()),
                dto.claveRegimen() == null ? null : new ClaveRegimen(dto.claveRegimen()),
                calificacion(dto.calificacion()),
                porcentaje(dto.tipoImpositivo()),
                importe(dto.baseImponibleOimporteNoSujeto()),
                importe(dto.baseImponibleACoste()),
                importe(dto.cuotaRepercutida()),
                porcentaje(dto.tipoRecargoEquivalencia()),
                importe(dto.cuotaRecargoEquivalencia()));
    }

    private static Impuesto impuesto(String codigo) {
        Impuesto impuesto = IMPUESTOS_POR_CODIGO.get(codigo.strip());
        if (impuesto == null) {
            throw new ValorInvalidoException("Código de impuesto desconocido: " + codigo
                    + ". Admitidos: " + IMPUESTOS_POR_CODIGO.keySet().stream().sorted().toList());
        }
        return impuesto;
    }

    /**
     * El XSD declara la calificación y la exención como un {@code choice}, y sus códigos no se
     * solapan: los de exención empiezan por E y los de calificación por S o N. Se reparte por
     * ahí en vez de pedir al integrador dos campos de los que solo puede rellenar uno.
     */
    private static Calificacion calificacion(String codigo) {
        String limpio = codigo.strip().toUpperCase(Locale.ROOT);
        try {
            return limpio.startsWith("E")
                    ? OperacionExenta.valueOf(limpio)
                    : CalificacionOperacion.valueOf(limpio);
        } catch (IllegalArgumentException e) {
            throw new ValorInvalidoException("Calificación desconocida: " + codigo
                    + ". Admitidas: " + Arrays.toString(CalificacionOperacion.values())
                    + " y " + Arrays.toString(OperacionExenta.values()));
        }
    }

    private static ImporteRectificacion rectificacion(PeticionAlta.Rectificacion dto) {
        return dto == null ? null : new ImporteRectificacion(
                importe(dto.baseRectificada()),
                importe(dto.cuotaRectificada()),
                importe(dto.cuotaRecargoRectificado()));
    }

    private static Importe importe(BigDecimal valor) {
        return valor == null ? null : new Importe(valor);
    }

    private static Porcentaje porcentaje(BigDecimal valor) {
        return valor == null ? null : new Porcentaje(valor);
    }
}
