package dev.lacre.remision.internal.adaptador;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.scheduling.config.FixedDelayTask;
import org.springframework.scheduling.config.ScheduledTask;
import org.springframework.scheduling.config.ScheduledTaskHolder;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

/** La programación del despachador, sin base de datos: qué la activa y con qué periodo corre. */
class ProgramacionDelDespachoTest {

    private final DespachadorLotes despachador = mock(DespachadorLotes.class);

    private final ApplicationContextRunner contexto = new ApplicationContextRunner()
            .withUserConfiguration(ProgramacionDelDespacho.class)
            .withBean(DespachadorLotes.class, () -> despachador);

    @Test
    void activoProgramaUnaTareaConElPeriodoConfigurado() {
        contexto.withPropertyValues(
                        "lacre.remision.despacho.activo=true",
                        "lacre.remision.despacho.periodo=7s")
                .run(arrancado -> assertThat(arrancado.getBean(ScheduledTaskHolder.class)
                        .getScheduledTasks())
                        .singleElement()
                        .extracting(ScheduledTask::getTask)
                        .isInstanceOfSatisfying(FixedDelayTask.class, tarea ->
                                assertThat(tarea.getIntervalDuration())
                                        .isEqualTo(Duration.ofSeconds(7))));
    }

    @Test
    void laTareaProgramadaLlamaAlDespachador() {
        contexto.withPropertyValues(
                        "lacre.remision.despacho.activo=true",
                        "lacre.remision.despacho.periodo=1h")
                .run(arrancado -> verify(despachador, timeout(5_000).atLeastOnce()).despachar());
    }

    @Test
    void desactivadoNoHayNadaProgramado() {
        contexto.withPropertyValues("lacre.remision.despacho.activo=false")
                .run(arrancado -> assertThat(arrancado)
                        .doesNotHaveBean(ProgramacionDelDespacho.class)
                        .doesNotHaveBean(ScheduledTaskHolder.class));
    }

    /** Sin la propiedad no se programa: el valor por omisión lo pone la configuración. */
    @Test
    void sinLaPropiedadNoHayNadaProgramado() {
        contexto.run(arrancado -> assertThat(arrancado)
                .doesNotHaveBean(ProgramacionDelDespacho.class));
    }
}
