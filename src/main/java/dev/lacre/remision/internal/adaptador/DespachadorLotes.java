package dev.lacre.remision.internal.adaptador;

import dev.lacre.identidad.ObligadoTributario;
import dev.lacre.identidad.Obligados;
import dev.lacre.remision.ClienteAeat;
import dev.lacre.remision.EnvioRegistro;
import dev.lacre.remision.Envios;
import dev.lacre.remision.LineaRespuesta;
import dev.lacre.remision.RemisionFallidaException;
import dev.lacre.remision.RespuestaRemision;
import dev.lacre.verifactu.consulta.RegistroRemitible;
import dev.lacre.verifactu.consulta.RegistrosRemitibles;
import dev.lacre.verifactu.registro.IdFactura;
import dev.lacre.verifactu.registro.TipoRegistro;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Lee el outbox y remite a la AEAT respetando el control de flujo del art. 16.2 de la
 * OM HAC/1177/2024.
 * <p>
 * <strong>El turno se reserva y se confirma antes de enviar</strong>, nunca durante. La
 * alternativa —mantener bloqueada la fila de control mientras dura la llamada— también sería
 * correcta, pero deja una transacción abierta durante una operación de red de hasta un minuto, y
 * con muchos obligados eso se come el pool de conexiones. El precio de reservar antes es que, si
 * el proceso muere entre la reserva y el envío, ese obligado pierde un turno. Se prefiere perder
 * un turno a tener transacciones colgando de la red, y sobre todo <strong>nunca se remite más
 * deprisa de lo que permite la norma</strong>, que es lo que no se puede incumplir.
 * <p>
 * No hay backoff propio: el control de flujo ya impone un mínimo entre envíos, así que es el
 * backoff. Escribir otro encima sería inventar una política que la norma ya fija.
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

    DespachadorLotes(ControlDeFlujo controlDeFlujo, ColaDeEnvios cola, Envios envios,
                     Obligados obligados, RegistrosRemitibles registros, ClienteAeat aeat,
                     Clock reloj, TransactionTemplate transaccion) {
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
     * Despacha un lote de cada obligado que tenga envíos pendientes y turno. Devuelve cuántos
     * lotes salieron de verdad.
     */
    public int despachar() {
        int lotes = 0;
        for (UUID obligadoId : controlDeFlujo.obligadosConPendientes()) {
            if (Boolean.TRUE.equals(transaccion.execute(estado -> despacharUnLoteDe(obligadoId)))) {
                lotes++;
            }
        }
        return lotes;
    }

    /**
     * La transacción cubre la reserva del lote —que es donde vive el {@code for update skip
     * locked}, y sin ella los bloqueos se soltarían al instante— y la escritura de los
     * desenlaces. La reserva del turno y la suma de intentos van en transacción propia, por lo
     * dicho arriba.
     * <p>
     * Se abre con {@code TransactionTemplate} y no con {@code @Transactional}: esto se llama
     * desde {@link #despachar()}, que está en esta misma clase, y una llamada interna no pasa por
     * el proxy de Spring. La anotación no daría error, simplemente <strong>no haría nada</strong>,
     * y el fallo sería invisible hasta que dos instancias se pisaran el mismo lote.
     */
    boolean despacharUnLoteDe(UUID obligadoId) {
        List<UUID> reservados = cola.reservarLoteDe(obligadoId, EscritorLote.MAXIMO_REGISTROS_POR_ENVIO);
        if (reservados.isEmpty()) {
            return false;
        }
        boolean loteLleno = reservados.size() >= EscritorLote.MAXIMO_REGISTROS_POR_ENVIO;
        if (!controlDeFlujo.reservarTurno(obligadoId, OffsetDateTime.now(reloj), loteLleno)) {
            return false;
        }

        Optional<ObligadoTributario> obligado = obligados.findById(obligadoId);
        if (obligado.isEmpty()) {
            log.error("Hay envíos pendientes del obligado {}, que ya no existe", obligadoId);
            return false;
        }

        List<EnvioRegistro> lote = cola.cargar(reservados);
        List<RegistroRemitible> remitibles = registros.de(
                lote.stream().map(EnvioRegistro::registroId).toList());
        if (remitibles.size() != lote.size()) {
            // No debería poder pasar: hay clave ajena. Si pasa, no se remite un lote a medias.
            log.error("El obligado {} tiene {} envíos pendientes pero solo {} registros guardados",
                    obligadoId, lote.size(), remitibles.size());
            return false;
        }

        try {
            RespuestaRemision respuesta = aeat.remitir(obligado.get(),
                    remitibles.stream().map(RegistroRemitible::xml).toList());
            controlDeFlujo.actualizarEspera(obligadoId, respuesta.tiempoEspera());
            aplicar(respuesta, lote, remitibles);
            return true;
        } catch (RemisionFallidaException e) {
            // No sabemos si la AEAT lo registró, así que el lote se queda PENDIENTE. Si sí había
            // entrado, el reintento traerá el código 3000 y se resolverá como DUPLICADO.
            cola.sumarIntento(lote);
            log.warn("No se pudo remitir el lote de {} registros del obligado {}: {}",
                    lote.size(), obligadoId, e.getMessage());
            return false;
        }
    }

    /**
     * Empareja cada respuesta con su envío por <strong>factura y tipo de operación</strong>, no
     * por posición: que la AEAT devuelva las líneas en el mismo orden en que se enviaron no lo
     * dice el documento en ninguna parte.
     * <p>
     * Lo que no se empareja se queda {@code PENDIENTE} y suma un intento. Un envío nunca se da
     * por resuelto sin una línea que lo diga.
     */
    private void aplicar(RespuestaRemision respuesta, List<EnvioRegistro> lote,
                         List<RegistroRemitible> remitibles) {
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
            cola.sumarIntento(sinRespuesta);
            log.error("La AEAT no devolvió línea para {} de los {} registros remitidos por el "
                            + "obligado {}; siguen pendientes",
                    sinRespuesta.size(), lote.size(), lote.getFirst().obligadoId());
        }
    }

    private EnvioRegistro resolver(EnvioRegistro envio, LineaRespuesta linea) {
        OffsetDateTime cuando = OffsetDateTime.now(reloj);
        return switch (linea.desenlace()) {
            case ACEPTADO -> envio.aceptado(cuando);
            case ACEPTADO_CON_ERRORES -> envio.aceptadoConErrores(
                    cuando, linea.codigoError(), linea.descripcionError());
            case RECHAZADO -> envio.rechazado(cuando, linea.codigoError(), linea.descripcionError());
            case DUPLICADO -> envio.duplicado(cuando, linea.descripcionError());
            case PENDIENTE -> envio;
        };
    }

    /** Lo que identifica de forma única un registro dentro de un lote. */
    private record Clave(IdFactura idFactura, TipoRegistro tipo) {
    }
}
