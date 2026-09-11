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
 * Conversores de Spring Data JDBC para los value objects que ocupan una columna: {@link Nif},
 * {@link Huella} y {@link ZoneId}.
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

    /** {@code ZoneId.of} rechaza las zonas inexistentes. */
    @ReadingConverter
    static class ColumnaAZona implements Converter<String, ZoneId> {
        @Override
        public ZoneId convert(String valor) {
            return ZoneId.of(valor);
        }
    }
}
