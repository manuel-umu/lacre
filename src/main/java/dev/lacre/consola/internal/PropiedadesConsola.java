package dev.lacre.consola.internal;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Credencial de la consola. Sin clave la consola queda cerrada; con una clave corta lacre no
 * arranca.
 */
@ConfigurationProperties("lacre.consola")
record PropiedadesConsola(String usuario, String clave) {

    static final int MINIMO_LONGITUD_CLAVE = 16;

    PropiedadesConsola {
        clave = clave == null ? "" : clave.strip();
        if (!clave.isEmpty() && clave.length() < MINIMO_LONGITUD_CLAVE) {
            throw new IllegalStateException("La clave de la consola (LACRE_CONSOLA_CLAVE) necesita al menos "
                    + MINIMO_LONGITUD_CLAVE + " caracteres y tiene " + clave.length()
                    + ". Genérala con: openssl rand -base64 24");
        }
        if (!clave.isEmpty() && (usuario == null || usuario.isBlank())) {
            throw new IllegalStateException("La consola tiene clave pero no usuario (LACRE_CONSOLA_USUARIO)");
        }
    }

    boolean abierta() {
        return !clave.isEmpty();
    }
}
