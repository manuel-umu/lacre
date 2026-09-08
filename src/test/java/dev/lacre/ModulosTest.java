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
     * Los subpaquetes publicados de {@code verifactu}: los tres de concepto más {@code evento},
     * que es por donde {@code remision} ve {@code RegistroCreado}. Si alguno perdiera su
     * {@code @NamedInterface} quedaría interno y los demás módulos dejarían de verlo: este test
     * lo detecta antes que un fallo de compilación a mitad de la Fase 6.
     */
    @Test
    void verifactuPublicaSusInterfacesDeConcepto() {
        assertThat(interfacesPublicadasDeVerifactu())
                .contains("registro", "desglose", "huella", "evento");
    }

    /**
     * El paquete raíz de {@code verifactu} está vacío desde que sus tipos se repartieron por
     * concepto, y tiene que seguir estándolo: cualquier clase que alguien deje ahí se convertiría
     * en API pública del módulo <strong>sin decidirlo</strong>, porque el raíz es la interfaz sin
     * nombre. Lo que se publica se publica a propósito.
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
