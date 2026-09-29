package dev.lacre.api.internal;

import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * Clave con la que los tests llaman a la API. Coincide con la de
 * {@code src/test/resources/application.properties}.
 */
public final class ApiDePrueba {

    public static final String CLAVE = "clave-de-pruebas-de-lacre-no-es-un-secreto";

    private ApiDePrueba() {}

    public static RequestPostProcessor autenticada() {
        return peticion -> {
            peticion.addHeader(HttpHeaders.AUTHORIZATION, "Bearer " + CLAVE);
            return peticion;
        };
    }
}
