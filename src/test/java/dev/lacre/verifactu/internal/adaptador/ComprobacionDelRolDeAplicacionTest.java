package dev.lacre.verifactu.internal.adaptador;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.lacre.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class ComprobacionDelRolDeAplicacionTest {

    @Autowired
    private JdbcClient jdbc;

    @Autowired
    private PostgreSQLContainer postgres;

    @Test
    void conectadaComoPropietariaLaAplicacionNoArranca() {
        JdbcClient propietario = TestcontainersConfiguration.comoPropietario(postgres);

        assertThatThrownBy(() -> new ComprobacionDelRolDeAplicacion(propietario))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(postgres.getUsername())
                .hasMessageContaining("lacre_app");
    }

    @Test
    void conectadaComoLacreAppArranca() {
        assertThatCode(() -> new ComprobacionDelRolDeAplicacion(jdbc)).doesNotThrowAnyException();
    }
}
