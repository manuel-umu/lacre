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
 * Da de alta en el outbox cada registro creado, dentro de la misma transacción: si falla, se
 * deshace también el registro.
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
