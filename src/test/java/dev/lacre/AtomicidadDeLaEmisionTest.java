package dev.lacre;

import dev.lacre.identidad.Obligados;
import dev.lacre.identidad.ObligadosDePrueba;
import dev.lacre.remision.Envios;
import dev.lacre.verifactu.internal.adaptador.CadenaDeRegistros;
import dev.lacre.verifactu.registro.Registros;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

/**
 * El test que prueba de verdad el
 * <a href="../../../docs/adr/0004-eventos-de-dominio-sincronos.md">ADR 0004</a>: si el alta en
 * el outbox falla, <strong>no queda registro de facturación</strong>.
 * <p>
 * Con {@code @ApplicationModuleListener} —{@code @Async} + {@code REQUIRES_NEW} +
 * {@code AFTER_COMMIT}— este test se pondría rojo: el registro habría confirmado antes de que el
 * oyente se ejecutase, y quedaría uno que no llegaría nunca a la AEAT.
 * <p>
 * Es preferible que el ERP no pueda facturar a que facture sin que el registro se remita.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class AtomicidadDeLaEmisionTest {

    @Autowired
    private CadenaDeRegistros cadena;

    @Autowired
    private Obligados obligados;

    @Autowired
    private JdbcClient jdbc;

    @MockitoBean
    private Envios envios;

    @Test
    void siElOutboxFallaNoQuedaRegistroDeFacturacion() {
        UUID obligado = ObligadosDePrueba.nuevo(obligados);
        given(envios.save(any())).willThrow(new IllegalStateException("outbox caído"));

        assertThatThrownBy(() -> cadena.anadir(obligado, Registros.alta().build()))
                .isInstanceOf(IllegalStateException.class);

        assertThat(registrosDe(obligado)).isZero();
    }

    private long registrosDe(UUID obligado) {
        return jdbc.sql("select count(*) from registro_facturacion where obligado_id = :obligado")
                .param("obligado", obligado)
                .query(Long.class)
                .single();
    }
}
