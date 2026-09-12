package dev.lacre;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

import static org.assertj.core.api.Assertions.assertThat;

/** Fronteras entre módulos según Spring Modulith. */
class ModulosTest {

    private final ApplicationModules modulos = ApplicationModules.of(LacreApplication.class);

    @Test
    void losModulosRespetanSusFronteras() {
        modulos.verify();
    }

    /** Las interfaces publicadas de {@code verifactu} conservan su {@code @NamedInterface}. */
    @Test
    void verifactuPublicaSusInterfacesDeConcepto() {
        assertThat(interfacesPublicadasDeVerifactu())
                .contains("registro", "desglose", "huella", "evento", "emision", "consulta", "qr");
    }

    /**
     * El paquete raíz de {@code verifactu} debe seguir vacío: es la interfaz sin nombre del
     * módulo.
     */
    @Test
    void elPaqueteRaizDeVerifactuNoPublicaNadaPorDescuido() {
        var sinNombre = modulos.getModuleByName("verifactu").orElseThrow()
                .getNamedInterfaces().getUnnamedInterface();

        assertThat(sinNombre).isEmpty();
    }

    @Test
    void elAdaptadorNoEstaPublicado() {
        assertThat(interfacesPublicadasDeVerifactu()).doesNotContain("internal", "adaptador");
    }

    private java.util.List<String> interfacesPublicadasDeVerifactu() {
        return modulos.getModuleByName("verifactu").orElseThrow()
                .getNamedInterfaces().stream()
                .map(nombrada -> nombrada.getName())
                .toList();
    }

    @Test
    void muestraLaEstructura() {
        System.out.println("MODULOS>> " + System.lineSeparator() + modulos);
    }
}
