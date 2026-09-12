package dev.lacre.remision;

import dev.lacre.remision.CatalogoErroresAeat.Clasificacion;
import dev.lacre.remision.CatalogoErroresAeat.ErrorAeat;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * El catálogo oficial, tal y como se lee del fichero de la AEAT. Los recuentos son la guardia:
 * si el fichero se sustituye por otro, aquí se ve.
 */
class CatalogoErroresAeatTest {

    @Test
    void lasTresListasTraenLosCodigosQueDeclaraElFicheroOficial() {
        assertThat(cuantosSon(Clasificacion.RECHAZO_ENVIO)).isEqualTo(44);
        assertThat(cuantosSon(Clasificacion.RECHAZO_FACTURA)).isEqualTo(193);
        assertThat(cuantosSon(Clasificacion.ADMISIBLE)).isEqualTo(10);
    }

    /** El prefijo del código no clasifica: los 3000 y los 3500 están en listas distintas. */
    @Test
    void elPrefijoDelCodigoNoDiceEnQueListaEsta() {
        assertThat(clasificacionDe(3000)).isEqualTo(Clasificacion.RECHAZO_FACTURA);
        assertThat(clasificacionDe(3500)).isEqualTo(Clasificacion.RECHAZO_ENVIO);
    }

    @Test
    void losDiezAdmisiblesSonLosDelDosMil() {
        for (int codigo = 2000; codigo <= 2009; codigo++) {
            assertThat(clasificacionDe(codigo)).isEqualTo(Clasificacion.ADMISIBLE);
        }
    }

    /** El fichero viene en ISO-8859-1: leído como UTF-8, las tildes saldrían rotas. */
    @Test
    void lasDescripcionesConservanLasTildes() {
        assertThat(CatalogoErroresAeat.de(4104).orElseThrow().descripcion())
                .isEqualTo("Error en la cabecera: el valor del campo NIF del bloque "
                        + "ObligadoEmision no está identificado.");
    }

    @Test
    void unCodigoQueNoEstaEnElCatalogoNoSeInventa() {
        assertThat(CatalogoErroresAeat.de(9999)).isEmpty();
        assertThat(CatalogoErroresAeat.de(null)).isEmpty();
    }

    private static long cuantosSon(Clasificacion clasificacion) {
        return CatalogoErroresAeat.todos().values().stream()
                .filter(error -> error.clasificacion() == clasificacion)
                .count();
    }

    private static Clasificacion clasificacionDe(int codigo) {
        return CatalogoErroresAeat.de(codigo).map(ErrorAeat::clasificacion).orElseThrow();
    }
}
