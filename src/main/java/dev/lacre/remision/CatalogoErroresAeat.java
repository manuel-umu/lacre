package dev.lacre.remision;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Catálogo de errores de la AEAT: qué significa un código y qué consecuencia tuvo. Se lee del
 * fichero oficial {@code errores.properties}, copiado literal.
 */
public final class CatalogoErroresAeat {

    private static final String RECURSO = "/aeat/errores.properties";

    private static final Pattern ENTRADA = Pattern.compile("^(\\d+)\\s*=\\s*(.*)$");

    private static final Map<Integer, ErrorAeat> POR_CODIGO = cargar();

    private CatalogoErroresAeat() {}

    /** Vacío si el código no está en el catálogo: la AEAT puede añadir códigos sin avisar. */
    public static Optional<ErrorAeat> de(Integer codigo) {
        return codigo == null ? Optional.empty() : Optional.ofNullable(POR_CODIGO.get(codigo));
    }

    static Map<Integer, ErrorAeat> todos() {
        return POR_CODIGO;
    }

    /**
     * Consecuencia de un error, según la lista del catálogo en la que está. Clasificar por el
     * rango del código no vale: los {@code 3000}-{@code 3004} rechazan la factura y los
     * {@code 3500}-{@code 3503}, el envío entero.
     */
    public enum Clasificacion {

        /** Ningún registro del envío llegó a procesarse. */
        RECHAZO_ENVIO,

        /** El registro no quedó presentado; se subsana con un registro nuevo. */
        RECHAZO_FACTURA,

        /** El registro quedó presentado y hay que subsanarlo después. */
        ADMISIBLE
    }

    public record ErrorAeat(int codigo, Clasificacion clasificacion, String descripcion) {}

    /**
     * Las tres listas del fichero van separadas por una línea de asteriscos, y llegan en el
     * mismo orden que {@link Clasificacion}. Se cuentan las cabeceras en vez de leer su texto,
     * que trae tildes y paréntesis.
     */
    private static Map<Integer, ErrorAeat> cargar() {
        Map<Integer, ErrorAeat> catalogo = new HashMap<>();
        Clasificacion[] listas = Clasificacion.values();
        int lista = -1;

        try (InputStream fichero = CatalogoErroresAeat.class.getResourceAsStream(RECURSO)) {
            if (fichero == null) {
                throw new IllegalStateException("Falta el catálogo de errores en " + RECURSO);
            }
            // El fichero oficial viene en ISO-8859-1, y se guarda tal cual.
            BufferedReader lineas = new BufferedReader(new InputStreamReader(fichero, StandardCharsets.ISO_8859_1));

            for (String linea = lineas.readLine(); linea != null; linea = lineas.readLine()) {
                if (linea.startsWith("*")) {
                    lista++;
                    continue;
                }
                Matcher entrada = ENTRADA.matcher(linea);
                if (!entrada.matches()) {
                    continue;
                }
                if (lista < 0 || lista >= listas.length) {
                    throw new IllegalStateException(
                            "El catálogo de errores tiene más listas de las esperadas: " + linea);
                }
                int codigo = Integer.parseInt(entrada.group(1));
                catalogo.put(
                        codigo,
                        new ErrorAeat(codigo, listas[lista], entrada.group(2).trim()));
            }
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo leer el catálogo de errores", e);
        }
        return Map.copyOf(catalogo);
    }
}
