package dev.lacre;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Postgres real para los tests, nunca una base de datos en memoria: el proyecto depende de
 * cosas que H2 no tiene —triggers en plpgsql, {@code pg_advisory_xact_lock},
 * {@code for update skip locked}— y un test que pasara contra H2 no probaría nada.
 * <p>
 * La versión va anclada, no en {@code latest}: una imagen que cambia sola convierte cualquier
 * fallo en un misterio.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

	@Bean
	@ServiceConnection
	PostgreSQLContainer postgresContainer() {
		return new PostgreSQLContainer(DockerImageName.parse("postgres:17-alpine"));
	}

}
