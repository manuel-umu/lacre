package dev.lacre.identidad;

import dev.lacre.shared.Nif;
import dev.lacre.shared.Textos;
import dev.lacre.shared.ValorInvalidoException;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.relational.core.mapping.Table;

import java.time.ZoneId;
import java.util.UUID;

/**
 * Obligado tributario por cuya cuenta se expiden facturas (art. 3 del RD 1007/2023). Su zona
 * horaria entra en el cálculo de la huella de cada registro.
 */
@Table("obligado")
public record ObligadoTributario(
        @Id UUID id,
        Nif nif,
        String nombreRazon,
        ZoneId zonaHoraria,
        @Version long version) {

    public ObligadoTributario {
        if (id == null) {
            throw new ValorInvalidoException("El obligado necesita identificador");
        }
        if (nif == null) {
            throw new ValorInvalidoException("El obligado necesita NIF");
        }
        if (zonaHoraria == null) {
            throw new ValorInvalidoException(
                    "El obligado necesita zona horaria: entra en el cálculo de la huella");
        }
        nombreRazon = Textos.obligatorio(nombreRazon, 120, "El nombre o razón social del obligado");
    }

    /** Alta de un obligado; la versión la gestiona Spring Data JDBC. */
    public static ObligadoTributario nuevo(UUID id, Nif nif, String nombreRazon, ZoneId zonaHoraria) {
        return new ObligadoTributario(id, nif, nombreRazon, zonaHoraria, 0);
    }

    /**
     * El mismo obligado con otros datos. La zona nueva solo afecta a los registros que se generen
     * después: cada registro guarda el huso con el que se calculó su huella.
     */
    public ObligadoTributario conDatos(String nombreRazon, ZoneId zonaHoraria) {
        return new ObligadoTributario(id, nif, nombreRazon, zonaHoraria, version);
    }
}
