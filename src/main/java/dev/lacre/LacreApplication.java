package dev.lacre;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.modulith.Modulithic;

/** Punto de entrada de la aplicación. {@code shared} se declara módulo compartido de Modulith. */
@Modulithic(sharedModules = "shared")
@SpringBootApplication
public class LacreApplication {

    public static void main(String[] args) {
        SpringApplication.run(LacreApplication.class, args);
    }
}
