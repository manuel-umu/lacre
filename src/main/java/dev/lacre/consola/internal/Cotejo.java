package dev.lacre.consola.internal;

import dev.lacre.remision.EstadoEnvio;
import dev.lacre.remision.RegistroEnAeat;
import dev.lacre.verifactu.consulta.RegistroDeFactura;
import dev.lacre.verifactu.registro.IdFactura;
import dev.lacre.verifactu.registro.TipoRegistro;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Cruce, factura a factura, de lo que la AEAT tiene presentado de un obligado en un periodo con
 * lo que lacre registró y la AEAT aceptó.
 */
public record Cotejo(
        YearMonth periodo,
        List<IdFactura> coinciden,
        List<Discrepancia> discrepancias,
        List<RegistroEnAeat> soloEnAeat,
        List<RegistroDeFactura> soloEnLacre,
        boolean completo) {

    /** Envíos cuyo registro consta en la AEAT. */
    private static final Set<EstadoEnvio> PRESENTADOS =
            Set.of(EstadoEnvio.ACEPTADO, EstadoEnvio.ACEPTADO_CON_ERRORES, EstadoEnvio.DUPLICADO);

    /** Una factura que está en los dos sitios y no cuadra. */
    public record Discrepancia(IdFactura idFactura, String motivo) {}

    /**
     * @param enAeat lo que devolvió la consulta del periodo
     * @param locales los registros de lacre de las facturas expedidas en el periodo y de las que
     *     devolvió la AEAT, sin repetir
     * @param estados el estado del envío de cada registro local
     */
    static Cotejo de(
            YearMonth periodo,
            List<RegistroEnAeat> enAeat,
            boolean completo,
            Collection<RegistroDeFactura> locales,
            Map<UUID, EstadoEnvio> estados) {
        Map<IdFactura, List<RegistroDeFactura>> porFactura = new HashMap<>();
        for (RegistroDeFactura registro : locales) {
            porFactura
                    .computeIfAbsent(registro.idFactura(), f -> new ArrayList<>())
                    .add(registro);
        }

        List<IdFactura> coinciden = new ArrayList<>();
        List<Discrepancia> discrepancias = new ArrayList<>();
        List<RegistroEnAeat> soloEnAeat = new ArrayList<>();
        Set<IdFactura> vistas = new LinkedHashSet<>();
        for (RegistroEnAeat registro : enAeat) {
            vistas.add(registro.idFactura());
            List<RegistroDeFactura> suyos = porFactura.getOrDefault(registro.idFactura(), List.of());
            if (suyos.isEmpty()) {
                soloEnAeat.add(registro);
                continue;
            }
            Optional<String> motivo = discrepancia(registro, ultimoPresentado(suyos, estados));
            if (motivo.isPresent()) {
                discrepancias.add(new Discrepancia(registro.idFactura(), motivo.get()));
            } else {
                coinciden.add(registro.idFactura());
            }
        }

        List<RegistroDeFactura> soloEnLacre = new ArrayList<>();
        porFactura.forEach((factura, suyos) -> {
            if (vistas.contains(factura) || !periodoDe(suyos).equals(periodo)) {
                return;
            }
            ultimoPresentado(suyos, estados).ifPresent(soloEnLacre::add);
        });
        soloEnLacre.sort(Comparator.comparingLong(RegistroDeFactura::posicion));

        return new Cotejo(periodo, coinciden, discrepancias, soloEnAeat, soloEnLacre, completo);
    }

    public boolean cuadra() {
        return discrepancias.isEmpty() && soloEnAeat.isEmpty() && soloEnLacre.isEmpty();
    }

    /** Para una factura anulada, la AEAT devuelve la huella de la anulación. */
    private static Optional<String> discrepancia(RegistroEnAeat enAeat, Optional<RegistroDeFactura> local) {
        if (local.isEmpty()) {
            return Optional.of("lacre no tiene ningún registro de esta factura aceptado por la AEAT");
        }
        RegistroDeFactura ultimo = local.get();
        boolean anuladaEnAeat = enAeat.estado() == RegistroEnAeat.Estado.ANULADO;
        boolean anuladaEnLacre = ultimo.tipo() == TipoRegistro.ANULACION;
        if (anuladaEnAeat && !anuladaEnLacre) {
            return Optional.of("la AEAT la tiene anulada y lacre no");
        }
        if (!anuladaEnAeat && anuladaEnLacre) {
            return Optional.of("lacre la anuló y la AEAT no la tiene anulada");
        }
        if (enAeat.huella() != null && !enAeat.huella().equals(ultimo.huella())) {
            return Optional.of("la huella vigente en la AEAT no es la del último registro aceptado en lacre");
        }
        return Optional.empty();
    }

    private static Optional<RegistroDeFactura> ultimoPresentado(
            List<RegistroDeFactura> suyos, Map<UUID, EstadoEnvio> estados) {
        return suyos.stream()
                .filter(registro -> PRESENTADOS.contains(estados.get(registro.id())))
                .max(Comparator.comparingLong(RegistroDeFactura::posicion));
    }

    /** El de la última alta de la factura; si solo hay anulaciones, el de su fecha de expedición. */
    private static YearMonth periodoDe(List<RegistroDeFactura> suyos) {
        return suyos.stream()
                .filter(registro -> registro.tipo() == TipoRegistro.ALTA)
                .max(Comparator.comparingLong(RegistroDeFactura::posicion))
                .orElse(suyos.getFirst())
                .periodoDeImputacion();
    }
}
