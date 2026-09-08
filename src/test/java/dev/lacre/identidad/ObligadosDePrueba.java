package dev.lacre.identidad;

import dev.lacre.shared.Nif;

import java.time.ZoneId;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Da de alta obligados con NIF válido y distinto en cada llamada.
 * <p>
 * El contenedor de Postgres se reutiliza entre tests y el NIF es único en la tabla, así que el
 * contador es de toda la JVM: dos clases de test que pidan obligados no pueden chocar. El
 * carácter de control se calcula, porque {@link Nif} valida de verdad.
 */
public final class ObligadosDePrueba {

    public static final ZoneId MADRID = ZoneId.of("Europe/Madrid");
    public static final ZoneId CANARIAS = ZoneId.of("Atlantic/Canary");

    private static final AtomicInteger SECUENCIA = new AtomicInteger();

    private ObligadosDePrueba() {
    }

    public static UUID nuevo(Obligados obligados) {
        return nuevo(obligados, MADRID);
    }

    public static UUID nuevo(Obligados obligados, ZoneId zona) {
        UUID id = UUID.randomUUID();
        obligados.save(ObligadoTributario.nuevo(id, siguienteNif(), "Obligado de prueba SL", zona));
        return id;
    }

    private static Nif siguienteNif() {
        String digitos = "%08d".formatted(SECUENCIA.incrementAndGet());
        return new Nif(digitos + "TRWAGMYFPDXBNJZSQVHLCKE".charAt(Integer.parseInt(digitos) % 23));
    }
}
