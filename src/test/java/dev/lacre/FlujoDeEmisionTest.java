package dev.lacre;

import static org.assertj.core.api.Assertions.assertThat;

import dev.lacre.identidad.Obligados;
import dev.lacre.identidad.ObligadosDePrueba;
import dev.lacre.remision.EnvioRegistro;
import dev.lacre.remision.Envios;
import dev.lacre.remision.EstadoEnvio;
import dev.lacre.verifactu.internal.adaptador.CadenaDeRegistros;
import dev.lacre.verifactu.internal.adaptador.RegistroFacturacion;
import dev.lacre.verifactu.registro.Registros;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/**
 * La frontera {@code verifactu} → {@code remision} de extremo a extremo: emitir deja el registro
 * en la cadena y su fila en el outbox.
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

        RegistroFacturacion registro =
                cadena.anadir(obligado, Registros.emitible().build());

        assertThat(envios.findByRegistroId(registro.id())).hasValueSatisfying(envio -> {
            assertThat(envio.estado()).isEqualTo(EstadoEnvio.PENDIENTE);
            assertThat(envio.creadoEn()).isNotNull();
        });
    }

    /** El desenlace se guarda con un {@code UPDATE}: la versión sube. */
    @Test
    void elDesenlaceDeUnEnvioSeGuardaYSeRecupera() {
        UUID obligado = ObligadosDePrueba.nuevo(obligados);
        RegistroFacturacion registro =
                cadena.anadir(obligado, Registros.emitible().build());
        EnvioRegistro pendiente = envios.findByRegistroId(registro.id()).orElseThrow();

        envios.save(pendiente.aceptadoConErrores(
                OffsetDateTime.now(), 2000, "El cálculo de la huella suministrada es incorrecta."));

        assertThat(envios.findByRegistroId(registro.id())).hasValueSatisfying(guardado -> {
            assertThat(guardado.estado()).isEqualTo(EstadoEnvio.ACEPTADO_CON_ERRORES);
            assertThat(guardado.codigoError()).isEqualTo(2000);
            assertThat(guardado.descripcionError()).startsWith("El cálculo de la huella");
            assertThat(guardado.enviadoEn()).isNotNull();
            assertThat(guardado.version()).isGreaterThan(pendiente.version());
        });
    }

    @Test
    void cadaRegistroDeLaCadenaTieneElSuyo() {
        UUID obligado = ObligadosDePrueba.nuevo(obligados);

        RegistroFacturacion primero =
                cadena.anadir(obligado, Registros.emitible().build());
        RegistroFacturacion segundo = cadena.anadir(
                obligado,
                Registros.emitible()
                        .idFactura(Registros.idFacturaEmitible("FA/2"))
                        .build());

        assertThat(envios.findByRegistroId(primero.id())).isPresent();
        assertThat(envios.findByRegistroId(segundo.id())).isPresent();
        assertThat(envios.count()).isGreaterThanOrEqualTo(2);
    }
}
