package dev.lacre.verifactu.internal.adaptador;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.lacre.TestcontainersConfiguration;
import dev.lacre.identidad.Obligados;
import dev.lacre.identidad.ObligadosDePrueba;
import dev.lacre.shared.Importe;
import dev.lacre.shared.Porcentaje;
import dev.lacre.shared.ReglaAeatIncumplidaException;
import dev.lacre.verifactu.desglose.CalificacionOperacion;
import dev.lacre.verifactu.desglose.Desglose;
import dev.lacre.verifactu.desglose.DetalleDesglose;
import dev.lacre.verifactu.desglose.Impuesto;
import dev.lacre.verifactu.registro.Registros;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;

/** La emisión aplica las claves de régimen del IPSI con la fecha de su reloj. */
@Import({TestcontainersConfiguration.class, ClavesDeRegimenIpsiEnLaEmisionTest.EneroDe2027.class})
@SpringBootTest
class ClavesDeRegimenIpsiEnLaEmisionTest {

    @Autowired
    private CadenaDeRegistros cadena;

    @Autowired
    private Obligados obligados;

    @Test
    void enEneroDe2027UnaLineaDeIpsiSinClaveNoEntraEnLaCadena() {
        UUID obligado = ObligadosDePrueba.nuevo(obligados);
        var alta = Registros.emitible()
                .desglose(Desglose.de(new DetalleDesglose(
                        Impuesto.IPSI,
                        null,
                        CalificacionOperacion.S1,
                        Porcentaje.de("10"),
                        Importe.de("111.10"),
                        null,
                        Importe.de("12.35"),
                        null,
                        null)))
                .build();

        assertThatThrownBy(() -> cadena.emitir(obligado, alta))
                .isInstanceOfSatisfying(
                        ReglaAeatIncumplidaException.class,
                        e -> assertThat(e.codigoAeat()).isEqualTo("1245"));
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class EneroDe2027 {

        @Bean
        @Primary
        Clock relojDeEneroDe2027() {
            return Clock.fixed(Instant.parse("2027-01-15T10:00:00Z"), ZoneOffset.UTC);
        }
    }
}
