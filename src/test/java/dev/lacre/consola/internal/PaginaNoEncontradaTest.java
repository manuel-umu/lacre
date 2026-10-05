package dev.lacre.consola.internal;

import static org.assertj.core.api.Assertions.assertThat;

import dev.lacre.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClient;

/** La página 404 con el servidor real, que es quien despacha a {@code /error}. */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class PaginaNoEncontradaTest {

    @LocalServerPort
    private int puerto;

    @Test
    void unNavegadorRecibeLaPaginaDeLacre() {
        ResponseEntity<String> respuesta = pedir("/no-existe", MediaType.TEXT_HTML);

        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(respuesta.getBody()).contains("Aquí no hay nada sellado").contains("/logo.svg");
    }

    @Test
    void unClienteDeLaApiSigueRecibiendoJson() {
        ResponseEntity<String> respuesta = pedir("/no-existe", MediaType.APPLICATION_JSON);

        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(respuesta.getHeaders().getContentType()).isNotNull();
        assertThat(respuesta.getHeaders().getContentType().isCompatibleWith(MediaType.APPLICATION_JSON))
                .isTrue();
    }

    @Test
    void elLogoEsPublico() {
        assertThat(pedir("/logo.svg", MediaType.ALL).getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    private ResponseEntity<String> pedir(String ruta, MediaType acepta) {
        return RestClient.create("http://localhost:" + puerto)
                .get()
                .uri(ruta)
                .accept(acepta)
                .retrieve()
                .onStatus(estado -> true, (peticion, respuesta) -> {})
                .toEntity(String.class);
    }
}
