package dev.lacre;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

import dev.lacre.identidad.Obligados;
import dev.lacre.identidad.ObligadosDePrueba;
import dev.lacre.remision.Envios;
import dev.lacre.verifactu.internal.adaptador.CadenaDeRegistros;
import dev.lacre.verifactu.registro.Registros;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * Si el alta en el outbox falla, no queda registro de facturación: el oyente corre en la misma
 * transacción.
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

        assertThatThrownBy(() -> cadena.anadir(obligado, Registros.emitible().build()))
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
