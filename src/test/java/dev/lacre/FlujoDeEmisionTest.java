package dev.lacre;

import dev.lacre.identidad.Obligados;
import dev.lacre.identidad.ObligadosDePrueba;
import dev.lacre.remision.EstadoEnvio;
import dev.lacre.remision.Envios;
import dev.lacre.verifactu.internal.adaptador.CadenaDeRegistros;
import dev.lacre.verifactu.internal.adaptador.RegistroFacturacion;
import dev.lacre.verifactu.registro.Registros;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * La frontera {@code verifactu} → {@code remision}, de extremo a extremo: emitir deja el
 * registro en la cadena y su fila en el outbox.
 * <p>
 * Vive en el paquete raíz y no dentro de un módulo porque cruza dos, y ninguno de los dos es su
 * dueño.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class FlujoDeEmisionTest {

    @Autowired
    private CadenaDeRegistros cadena;

    @Autowired
    private Envios envios;

    @Autowired
    private Obligados obligados;

    @Test
    void cadaRegistroCreadoDejaSuEnvioPendiente() {
        UUID obligado = ObligadosDePrueba.nuevo(obligados);

        RegistroFacturacion registro = cadena.anadir(obligado, Registros.alta().build());

        assertThat(envios.findByRegistroId(registro.id())).hasValueSatisfying(envio -> {
            assertThat(envio.estado()).isEqualTo(EstadoEnvio.PENDIENTE);
            assertThat(envio.creadoEn()).isNotNull();
        });
    }

    @Test
    void cadaRegistroDeLaCadenaTieneElSuyo() {
        UUID obligado = ObligadosDePrueba.nuevo(obligados);

        RegistroFacturacion primero = cadena.anadir(obligado, Registros.alta().build());
        RegistroFacturacion segundo = cadena.anadir(obligado,
                Registros.alta().idFactura(Registros.idFactura("FA/2")).build());

        assertThat(envios.findByRegistroId(primero.id())).isPresent();
        assertThat(envios.findByRegistroId(segundo.id())).isPresent();
        assertThat(envios.count()).isGreaterThanOrEqualTo(2);
    }
}
