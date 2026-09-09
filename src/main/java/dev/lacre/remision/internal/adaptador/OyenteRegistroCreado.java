package dev.lacre.remision.internal.adaptador;

import dev.lacre.remision.EnvioRegistro;
import dev.lacre.remision.Envios;
import dev.lacre.verifactu.evento.RegistroCreado;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Da de alta en el outbox cada registro que se crea.
 * <p>
 * <strong>{@code @EventListener} y no {@code @ApplicationModuleListener}</strong>: esa anotación
 * es {@code @Async} + {@code REQUIRES_NEW} + {@code AFTER_COMMIT}, así que correría después de
 * confirmar la transacción del emisor y en otra distinta. Eso deja una ventana con el registro
 * ya escrito y sin fila en el outbox, y un registro sin outbox no llega nunca a la AEAT. Aquí
 * las dos escrituras son el mismo hecho. Ver el
 * <a href="../../../../../../docs/adr/0004-eventos-de-dominio-sincronos.md">ADR 0004</a>.
 * <p>
 * Corre dentro de la transacción que abrió el caso de uso, así que si esto falla se deshace
 * también el registro. Es lo que se quiere: preferible no poder facturar a facturar sin remitir.
 */
@Component
class OyenteRegistroCreado {

    private final Envios envios;
    private final Clock reloj;
    private final Supplier<UUID> generadorDeIdentificadores;

    OyenteRegistroCreado(Envios envios, Clock reloj, Supplier<UUID> generadorDeIdentificadores) {
        this.envios = envios;
        this.reloj = reloj;
        this.generadorDeIdentificadores = generadorDeIdentificadores;
    }

    @EventListener
    void alCrearseUnRegistro(RegistroCreado evento) {
        envios.save(EnvioRegistro.pendiente(generadorDeIdentificadores.get(),
                evento.registroId(), evento.obligadoId(), OffsetDateTime.now(reloj)));
    }
}
