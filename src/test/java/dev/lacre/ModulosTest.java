package dev.lacre;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Comprobación de las fronteras entre módulos que hace Spring Modulith.
 * <p>
 * Complementa a {@code ArquitecturaTest}: aquella fija reglas nuestras —qué no puede depender
 * de Spring, quién no entra en {@code internal}—, y esta comprueba que los módulos declarados
 * no se saltan sus interfaces publicadas.
 */
class ModulosTest {

    private final ApplicationModules modulos = ApplicationModules.of(LacreApplication.class);

    @Test
    void losModulosRespetanSusFronteras() {
        modulos.verify();
    }

    /**
     * Los tres subpaquetes de concepto de {@code verifactu} tienen que estar publicados como
     * interfaces con nombre. Si alguno perdiera su {@code @NamedInterface}, quedaría interno y
     * los demás módulos dejarían de verlo: este test lo detecta antes que un fallo de compilación
     * a mitad de la Fase 6.
     */
    @Test
    void verifactuPublicaSusTresInterfacesDeConcepto() {
        var interfacesPublicadas = modulos.getModuleByName("verifactu").orElseThrow()
                .getNamedInterfaces().stream()
                .map(nombrada -> nombrada.getName())
                .toList();

        assertThat(interfacesPublicadas).contains("registro", "desglose", "huella");
    }

    @Test
    void elAdaptadorNoEstaPublicado() {
        var interfacesPublicadas = modulos.getModuleByName("verifactu").orElseThrow()
                .getNamedInterfaces().stream()
                .map(nombrada -> nombrada.getName())
                .toList();

        assertThat(interfacesPublicadas).doesNotContain("internal", "adaptador");
    }

    @Test
    void muestraLaEstructura() {
        System.out.println("MODULOS>> " + System.lineSeparator() + modulos);
    }
}
