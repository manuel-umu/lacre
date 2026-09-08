package dev.lacre;

import dev.lacre.shared.Huella;
import dev.lacre.shared.Nif;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.convert.ReadingConverter;
import org.springframework.data.convert.WritingConverter;
import org.springframework.data.jdbc.repository.config.AbstractJdbcConfiguration;

import java.time.ZoneId;
import java.util.List;

/**
 * Conversores de los value objects para Spring Data JDBC.
 * <p>
 * Viven aquí, en el paquete raíz de la aplicación, y <strong>no en {@code shared}</strong>:
 * {@code shared} viaja con la librería que se publica en Maven Central y no puede depender de
 * Spring. Es una consecuencia directa del ADR 0002, y la vigila {@code ArquitecturaTest}.
 * <p>
 * Están los de {@link Nif}, {@link Huella} y {@link ZoneId}, que son los tipos que hoy ocupan
 * una columna. {@code Importe} y {@code Porcentaje} viajan dentro del XML y no la necesitan.
 */
@Configuration(proxyBeanMethods = false)
public class ConfiguracionJdbc extends AbstractJdbcConfiguration {

    @Override
    protected List<?> userConverters() {
        return List.of(
                new NifAColumna(), new ColumnaANif(),
                new HuellaAColumna(), new ColumnaAHuella(),
                new ZonaAColumna(), new ColumnaAZona());
    }

    @WritingConverter
    static class NifAColumna implements Converter<Nif, String> {
        @Override
        public String convert(Nif nif) {
            return nif.valor();
        }
    }

    @ReadingConverter
    static class ColumnaANif implements Converter<String, Nif> {
        @Override
        public Nif convert(String valor) {
            return new Nif(valor);
        }
    }

    @WritingConverter
    static class HuellaAColumna implements Converter<Huella, String> {
        @Override
        public String convert(Huella huella) {
            return huella.valor();
        }
    }

    @ReadingConverter
    static class ColumnaAHuella implements Converter<String, Huella> {
        @Override
        public Huella convert(String valor) {
            return new Huella(valor);
        }
    }

    @WritingConverter
    static class ZonaAColumna implements Converter<ZoneId, String> {
        @Override
        public String convert(ZoneId zona) {
            return zona.getId();
        }
    }

    /** {@code ZoneId.of} rechaza una zona que no exista, que es la validación que queremos. */
    @ReadingConverter
    static class ColumnaAZona implements Converter<String, ZoneId> {
        @Override
        public ZoneId convert(String valor) {
            return ZoneId.of(valor);
        }
    }
}
