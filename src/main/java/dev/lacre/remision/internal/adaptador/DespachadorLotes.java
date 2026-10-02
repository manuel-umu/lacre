package dev.lacre.remision.internal.adaptador;

import dev.lacre.identidad.CertificadoNoDisponibleException;
import dev.lacre.identidad.ObligadoTributario;
import dev.lacre.identidad.Obligados;
import dev.lacre.remision.ClienteAeat;
import dev.lacre.remision.EnvioRechazadoException;
import dev.lacre.remision.EnvioRegistro;
import dev.lacre.remision.Envios;
import dev.lacre.remision.LineaRespuesta;
import dev.lacre.remision.RemisionFallidaException;
import dev.lacre.remision.RespuestaRemision;
import dev.lacre.verifactu.consulta.RegistroRemitible;
import dev.lacre.verifactu.consulta.RegistrosRemitibles;
import dev.lacre.verifactu.registro.IdFactura;
import dev.lacre.verifactu.registro.TipoRegistro;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Lee el outbox y remite lotes a la AEAT respetando el control de flujo. El turno se reserva y
 * confirma antes de enviar, nunca durante.
 */
@Component
public class DespachadorLotes {

    private static final Logger log = LoggerFactory.getLogger(DespachadorLotes.class);

    private final ControlDeFlujo controlDeFlujo;
    private final ColaDeEnvios cola;
    private final Envios envios;
    private final Obligados obligados;
    private final RegistrosRemitibles registros;
    private final ClienteAeat aeat;
    private final Clock reloj;
    private final TransactionTemplate transaccion;

    DespachadorLotes(
            ControlDeFlujo controlDeFlujo,
            ColaDeEnvios cola,
            Envios envios,
            Obligados obligados,
            RegistrosRemitibles registros,
            ClienteAeat aeat,
            Clock reloj,
            TransactionTemplate transaccion) {
        this.controlDeFlujo = controlDeFlujo;
        this.cola = cola;
        this.envios = envios;
        this.obligados = obligados;
        this.registros = registros;
        this.aeat = aeat;
        this.reloj = reloj;
        this.transaccion = transaccion;
    }

    /**
     * Despacha un lote de cada obligado con envíos pendientes y turno, y devuelve cuántos
     * salieron. Lo que falle en un obligado no impide despachar a los demás.
     */
    public int despachar() {
        int lotes = 0;
        for (UUID obligadoId : controlDeFlujo.obligadosConPendientes()) {
            try {
                Boolean salio = transaccion.execute(estado -> despacharUnLoteDe(obligadoId));
                if (Boolean.TRUE.equals(salio)) {
                    lotes++;
                }
            } catch (RuntimeException e) {
                log.error("Falló el despacho del obligado {}; se sigue con los demás", obligadoId, e);
            }
        }
        return lotes;
    }

    /**
     * Un lote de un obligado. Se ejecuta dentro de la transacción que abre {@link #despachar()},
     * que cubre la reserva del lote y la escritura de los desenlaces.
     */
    boolean despacharUnLoteDe(UUID obligadoId) {
        List<UUID> reservados = cola.reservarLoteDe(obligadoId, EscritorLote.MAXIMO_REGISTROS_POR_ENVIO);
        if (reservados.isEmpty()) {
            return false;
        }

        List<EnvioRegistro> reservadas = cola.cargar(reservados);
        List<RegistroRemitible> todos =
                registros.de(reservadas.stream().map(EnvioRegistro::registroId).toList());
        if (todos.size() != reservadas.size()) {
            // No debería pasar: hay clave ajena. No se remite un lote a medias.
            log.error(
                    "El obligado {} tiene {} envíos pendientes pero solo {} registros guardados",
                    obligadoId,
                    reservadas.size(),
                    todos.size());
            return false;
        }
        int corte = hastaLaPrimeraFacturaRepetida(todos);
        List<EnvioRegistro> lote = reservadas.subList(0, corte);
        List<RegistroRemitible> remitibles = todos.subList(0, corte);

        boolean loteLleno = lote.size() >= EscritorLote.MAXIMO_REGISTROS_POR_ENVIO;
        if (!controlDeFlujo.reservarTurno(obligadoId, OffsetDateTime.now(reloj), loteLleno)) {
            return false;
        }

        Optional<ObligadoTributario> obligado = obligados.findById(obligadoId);
        if (obligado.isEmpty()) {
            log.error("Hay envíos pendientes del obligado {}, que ya no existe", obligadoId);
            return false;
        }

        try {
            RespuestaRemision respuesta = aeat.remitir(
                    obligado.get(),
                    remitibles.stream().map(RegistroRemitible::xml).toList());
            controlDeFlujo.actualizarEspera(obligadoId, respuesta.tiempoEspera());
            aplicar(respuesta, lote, remitibles);
            return true;
        } catch (RemisionFallidaException e) {
            // No se sabe si la AEAT lo registró: el lote sigue PENDIENTE. Si había entrado, el
            // reintento devolverá el código 3000 y se resolverá como DUPLICADO.
            cola.sumarIntento(lote, codigoDe(e), e.getMessage());
            log.warn(
                    "No se pudo remitir el lote de {} registros del obligado {}: {}",
                    lote.size(),
                    obligadoId,
                    e.getMessage());
            return false;
        } catch (CertificadoNoDisponibleException e) {
            cola.sumarIntento(lote, null, e.getMessage());
            log.error(
                    "El obligado {} tiene {} envíos pendientes que no se pueden remitir: {}",
                    obligadoId,
                    lote.size(),
                    e.getMessage());
            return false;
        }
    }

    /**
     * Empareja cada línea de respuesta con su envío por factura y tipo de operación. Lo que no
     * se empareja sigue pendiente y suma un intento.
     */
    private void aplicar(RespuestaRemision respuesta, List<EnvioRegistro> lote, List<RegistroRemitible> remitibles) {
        Map<Clave, LineaRespuesta> porClave = new HashMap<>();
        for (LineaRespuesta linea : respuesta.lineas()) {
            porClave.put(new Clave(linea.idFactura(), linea.tipo()), linea);
        }

        List<EnvioRegistro> resueltos = new ArrayList<>();
        List<EnvioRegistro> sinRespuesta = new ArrayList<>();
        for (int i = 0; i < lote.size(); i++) {
            RegistroRemitible remitible = remitibles.get(i);
            LineaRespuesta linea = porClave.get(new Clave(remitible.idFactura(), remitible.tipo()));
            if (linea == null) {
                sinRespuesta.add(lote.get(i));
            } else {
                resueltos.add(resolver(lote.get(i), linea));
            }
        }

        envios.saveAll(resueltos);
        if (!sinRespuesta.isEmpty()) {
            cola.sumarIntento(sinRespuesta, null, "La AEAT respondió al lote sin decir nada de este registro");
            log.error(
                    "La AEAT no devolvió línea para {} de los {} registros remitidos por el "
                            + "obligado {}; siguen pendientes",
                    sinRespuesta.size(),
                    lote.size(),
                    lote.getFirst().obligadoId());
        }
    }

    /**
     * Cada factura va una sola vez por lote: el orden en que la AEAT procesa las líneas de un envío
     * no está especificado, y la respuesta se empareja por factura y tipo. Lo que queda fuera sale
     * en el lote siguiente.
     */
    private static int hastaLaPrimeraFacturaRepetida(List<RegistroRemitible> remitibles) {
        Set<IdFactura> facturas = new HashSet<>();
        for (int i = 0; i < remitibles.size(); i++) {
            if (!facturas.add(remitibles.get(i).idFactura())) {
                return i;
            }
        }
        return remitibles.size();
    }

    /** Un rechazo del envío completo llega con código; una caída de red, no. */
    private static Integer codigoDe(RemisionFallidaException e) {
        return e instanceof EnvioRechazadoException rechazo ? rechazo.codigo() : null;
    }

    private EnvioRegistro resolver(EnvioRegistro envio, LineaRespuesta linea) {
        OffsetDateTime cuando = OffsetDateTime.now(reloj);
        return switch (linea.desenlace()) {
            case ACEPTADO -> envio.aceptado(cuando);
            case ACEPTADO_CON_ERRORES ->
                envio.aceptadoConErrores(cuando, linea.codigoError(), linea.descripcionError());
            case RECHAZADO -> envio.rechazado(cuando, linea.codigoError(), linea.descripcionError());
            case DUPLICADO -> envio.duplicado(cuando, linea.descripcionError());
            case PENDIENTE -> envio;
            case APARTADO -> throw new IllegalStateException("La respuesta de la AEAT no aparta envíos");
        };
    }

    /** Identifica un registro dentro de un lote. */
    private record Clave(IdFactura idFactura, TipoRegistro tipo) {}
}
