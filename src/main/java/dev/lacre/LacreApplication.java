package dev.lacre;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.modulith.Modulithic;

/**
 * {@code shared} se declara módulo compartido: es el suelo de value objects sobre el que se
 * apoyan todos los módulos, no un módulo de negocio, y cualquiera puede depender de él sin
 * declararlo explícitamente.
 */
@Modulithic(sharedModules = "shared")
@SpringBootApplication
public class LacreApplication {

	public static void main(String[] args) {
		SpringApplication.run(LacreApplication.class, args);
	}

}
