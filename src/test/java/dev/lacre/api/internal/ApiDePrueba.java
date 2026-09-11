package dev.lacre.api.internal;

import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * La credencial con la que los tests llaman a la API.
 * <p>
 * El literal está repetido en {@code src/test/resources/application.properties}, que es de donde
 * la lee la aplicación. Podría leerse de ahí con {@code @Value}, pero entonces el test dependería
 * de la misma configuración que está probando: si la propiedad desapareciera, los dos lados
 * cambiarían a la vez y ningún test se pondría rojo.
 * <p>
 * Vive en la raíz de {@code api.internal} y no dentro de {@code emision} ni de
 * {@code autenticacion}: la usan los tests de los tres subpaquetes, y ponerla en uno haría que
 * los otros dos dependieran de él por un detalle de utilería.
 */
public final class ApiDePrueba {

    public static final String CLAVE = "clave-de-pruebas-de-lacre-no-es-un-secreto";

    private ApiDePrueba() {
    }

    public static RequestPostProcessor autenticada() {
        return peticion -> {
            peticion.addHeader(HttpHeaders.AUTHORIZATION, "Bearer " + CLAVE);
            return peticion;
        };
    }
}
