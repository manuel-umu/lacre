package dev.lacre.api.internal;

import dev.lacre.shared.IdOtro;
import dev.lacre.shared.IdentificadorFiscal;
import dev.lacre.shared.Importe;
import dev.lacre.shared.Nif;
import dev.lacre.shared.Porcentaje;
import dev.lacre.shared.TipoIdentificacion;
import dev.lacre.shared.ValorInvalidoException;
import dev.lacre.verifactu.desglose.Calificacion;
import dev.lacre.verifactu.desglose.CalificacionOperacion;
import dev.lacre.verifactu.desglose.ClaveRegimen;
import dev.lacre.verifactu.desglose.Desglose;
import dev.lacre.verifactu.desglose.DetalleDesglose;
import dev.lacre.verifactu.desglose.Impuesto;
import dev.lacre.verifactu.desglose.OperacionExenta;
import dev.lacre.verifactu.registro.DatosRegistro;
import dev.lacre.verifactu.registro.DatosRegistroAlta;
import dev.lacre.verifactu.registro.DatosRegistroAnulacion;
import dev.lacre.verifactu.registro.IdFactura;
import dev.lacre.verifactu.registro.ImporteRectificacion;
import dev.lacre.verifactu.registro.PersonaFisicaJuridica;
import dev.lacre.verifactu.registro.SistemaInformatico;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Traduce el contrato de la API al modelo fiscal. A mano y sin generador: MapStruct está
 * prohibido en el proyecto, y de todas formas la mitad de esto no es copiar campos sino
 * resolver catálogos y tipos sellados.
 * <p>
 * <strong>No revalida lo que ya valida el dominio.</strong> Los constructores de {@code shared}
 * y de {@link DatosRegistroAlta} comprueban longitudes, rangos y reglas de la AEAT; repetirlo
 * aquí garantizaría que un día las dos versiones discrepen. Lo único que se comprueba en este
 * fichero son las cosas que el dominio no puede ver porque nacen de la forma del JSON: que un
 * identificador fiscal no venga vacío ni por duplicado, y que un código de catálogo exista.
 */
@Component
class Mapeador {

    private static final Map<String, Impuesto> IMPUESTOS_POR_CODIGO =
            porCodigo(Impuesto.values(), Impuesto::codigo);

    private static final Map<String, TipoIdentificacion> IDENTIFICACIONES_POR_CODIGO =
            porCodigo(TipoIdentificacion.values(), TipoIdentificacion::codigo);

    private final SistemaInformatico sistemaInformatico;

    Mapeador(SistemaInformatico sistemaInformatico) {
        this.sistemaInformatico = sistemaInformatico;
    }

    /**
     * El {@code switch} sobre el tipo sellado es exhaustivo: si algún día hay una tercera clase
     * de petición, esto no compila hasta que se mapee.
     */
    DatosRegistro aDatos(PeticionRegistro peticion) {
        return switch (peticion) {
            case PeticionAlta alta -> aDatos(alta);
            case PeticionAnulacion anulacion -> aDatos(anulacion);
        };
    }

    private DatosRegistroAlta aDatos(PeticionAlta peticion) {
        return DatosRegistroAlta.builder()
                .idFactura(idFactura(peticion.idFactura()))
                .refExterna(peticion.refExterna())
                .nombreRazonEmisor(peticion.nombreRazonEmisor())
                .subsanacion(si(peticion.subsanacion()))
                .rechazoPrevio(peticion.rechazoPrevio())
                .tipoFactura(peticion.tipoFactura())
                .tipoRectificativa(peticion.tipoRectificativa())
                .facturasRectificadas(idsFactura(peticion.facturasRectificadas()))
                .facturasSustituidas(idsFactura(peticion.facturasSustituidas()))
                .importeRectificacion(rectificacion(peticion.importeRectificacion()))
                .fechaOperacion(peticion.fechaOperacion())
                .descripcionOperacion(peticion.descripcionOperacion())
                .facturaSimplificadaArt7273(si(peticion.facturaSimplificadaArt7273()))
                .facturaSinIdentifDestinatarioArt61d(si(peticion.facturaSinIdentifDestinatarioArt61d()))
                .macrodato(si(peticion.macrodato()))
                .emitidaPorTerceroODestinatario(peticion.emitidaPorTerceroODestinatario())
                .tercero(persona(peticion.tercero()))
                .destinatarios(personas(peticion.destinatarios()))
                .cupon(si(peticion.cupon()))
                .desglose(new Desglose(peticion.desglose().stream().map(Mapeador::detalle).toList()))
                .cuotaTotal(importe(peticion.cuotaTotal()))
                .importeTotal(importe(peticion.importeTotal()))
                .sistemaInformatico(sistemaInformatico)
                .numRegistroAcuerdoFacturacion(peticion.numRegistroAcuerdoFacturacion())
                .idAcuerdoSistemaInformatico(peticion.idAcuerdoSistemaInformatico())
                .build();
    }

    private DatosRegistroAnulacion aDatos(PeticionAnulacion peticion) {
        return new DatosRegistroAnulacion(
                idFactura(peticion.idFacturaAnulada()),
                peticion.refExterna(),
                si(peticion.sinRegistroPrevio()),
                si(peticion.rechazoPrevio()),
                peticion.generadoPor(),
                persona(peticion.generador()),
                sistemaInformatico);
    }

    static IdFactura idFactura(IdFacturaDto dto) {
        return new IdFactura(
                new Nif(dto.idEmisorFactura()), dto.numSerieFactura(), dto.fechaExpedicionFactura());
    }

    private static List<IdFactura> idsFactura(List<IdFacturaDto> dtos) {
        return dtos == null ? List.of() : dtos.stream().map(Mapeador::idFactura).toList();
    }

    private static DetalleDesglose detalle(PeticionAlta.Detalle dto) {
        return new DetalleDesglose(
                dto.impuesto() == null
                        ? null
                        : delCatalogo(IMPUESTOS_POR_CODIGO, dto.impuesto(), "impuesto"),
                dto.claveRegimen() == null ? null : new ClaveRegimen(dto.claveRegimen()),
                calificacion(dto.calificacion()),
                porcentaje(dto.tipoImpositivo()),
                importe(dto.baseImponibleOimporteNoSujeto()),
                importe(dto.baseImponibleACoste()),
                importe(dto.cuotaRepercutida()),
                porcentaje(dto.tipoRecargoEquivalencia()),
                importe(dto.cuotaRecargoEquivalencia()));
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

    private static List<PersonaFisicaJuridica> personas(List<PersonaDto> dtos) {
        return dtos == null ? List.of() : dtos.stream().map(Mapeador::persona).toList();
    }

    private static PersonaFisicaJuridica persona(PersonaDto dto) {
        return dto == null ? null : new PersonaFisicaJuridica(dto.nombreRazon(), identificador(dto));
    }

    private static IdentificadorFiscal identificador(PersonaDto dto) {
        boolean tieneNif = dto.nif() != null && !dto.nif().isBlank();
        if (tieneNif == (dto.idOtro() != null)) {
            throw new ValorInvalidoException(
                    "El destinatario o tercero " + dto.nombreRazon() + " debe identificarse con "
                            + "nif o con idOtro, exactamente uno de los dos: el XSD los declara "
                            + "excluyentes");
        }
        if (tieneNif) {
            return new Nif(dto.nif());
        }
        PersonaDto.IdOtroDto otro = dto.idOtro();
        return new IdOtro(otro.codigoPais(),
                delCatalogo(IDENTIFICACIONES_POR_CODIGO, otro.tipo(), "tipo de identificación"),
                otro.id());
    }

    private static ImporteRectificacion rectificacion(PeticionAlta.Rectificacion dto) {
        return dto == null ? null : new ImporteRectificacion(
                importe(dto.baseRectificada()),
                importe(dto.cuotaRectificada()),
                importe(dto.cuotaRecargoRectificado()));
    }

    /** Un indicador omitido vale «N», según el propio diseño de registro de la AEAT. */
    private static boolean si(Boolean indicador) {
        return Boolean.TRUE.equals(indicador);
    }

    private static Importe importe(BigDecimal valor) {
        return valor == null ? null : new Importe(valor);
    }

    private static Porcentaje porcentaje(BigDecimal valor) {
        return valor == null ? null : new Porcentaje(valor);
    }

    private static <T> T delCatalogo(Map<String, T> catalogo, String codigo, String que) {
        T valor = catalogo.get(codigo == null ? null : codigo.strip());
        if (valor == null) {
            throw new ValorInvalidoException("Código de " + que + " desconocido: " + codigo
                    + ". Admitidos: " + catalogo.keySet().stream().sorted().toList());
        }
        return valor;
    }

    private static <T> Map<String, T> porCodigo(T[] valores, Function<T, String> codigo) {
        return Arrays.stream(valores)
                .collect(Collectors.toUnmodifiableMap(codigo, valor -> valor));
    }
}
