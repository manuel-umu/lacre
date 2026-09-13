package dev.lacre.remision.internal.adaptador;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * Lanza el despachador de lotes periódicamente. Solo existe con
 * {@code lacre.remision.despacho.activo}. Varias instancias pueden despachar a la vez: el
 * despachador ya se coordina en la base de datos.
 */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
@ConditionalOnBooleanProperty("lacre.remision.despacho.activo")
class ProgramacionDelDespacho {

    private final DespachadorLotes despachador;

    ProgramacionDelDespacho(DespachadorLotes despachador) {
        this.despachador = despachador;
    }

    /** Espera entre el final de una pasada y el principio de la siguiente, no entre inicios. */
    @Scheduled(fixedDelayString = "${lacre.remision.despacho.periodo}")
    void despachar() {
        despachador.despachar();
    }
}
