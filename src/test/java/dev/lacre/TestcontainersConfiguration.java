package dev.lacre;

import org.postgresql.Driver;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.SimpleDriverDataSource;
import org.springframework.test.context.DynamicPropertyRegistrar;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Postgres real para los tests, con la versión de la imagen anclada y conectado como en un
 * despliegue: Flyway como propietario y la aplicación como {@code lacre_app}.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

	static final String CLAVE_APLICACION = "clave-de-pruebas-del-rol-de-aplicacion";

	@Bean
	PostgreSQLContainer postgresContainer() {
		return new PostgreSQLContainer(DockerImageName.parse("postgres:17-alpine"));
	}

	@Bean
	DynamicPropertyRegistrar conexionesComoEnUnDespliegue(PostgreSQLContainer postgres) {
		return propiedades -> {
			propiedades.add("spring.datasource.url", postgres::getJdbcUrl);
			propiedades.add("spring.datasource.username", () -> "lacre_app");
			propiedades.add("spring.datasource.password", () -> CLAVE_APLICACION);
			propiedades.add("spring.flyway.user", postgres::getUsername);
			propiedades.add("spring.flyway.password", postgres::getPassword);
			propiedades.add("spring.flyway.placeholders.clave_rol_aplicacion",
					() -> CLAVE_APLICACION);
		};
	}

	/** Conexión como propietario, para los tests que se saltan a propósito las defensas. */
	public static JdbcClient comoPropietario(PostgreSQLContainer postgres) {
		return JdbcClient.create(new SimpleDriverDataSource(new Driver(),
				postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword()));
	}

}
