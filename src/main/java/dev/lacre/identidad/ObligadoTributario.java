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
 * Obligado tributario por cuya cuenta se expiden facturas, en el sentido del art. 3 del
 * RD 1007/2023.
 * <p>
 * Su {@code zonaHoraria} <strong>entra en el cálculo de la huella</strong>: cada registro se
 * fecha con {@code FechaHoraHusoGenRegistro}, que se serializa con su desplazamiento, de modo
 * que el mismo instante en {@code Europe/Madrid} y en {@code Atlantic/Canary} produce dos
 * huellas distintas. Es dato del obligado y no del despliegue porque una sola instalación
 * factura para obligados de ambos sitios.
 * <p>
 * Lleva {@code @Version} porque es un agregado mutable: con un {@code @Id} asignado a mano,
 * Spring Data JDBC no puede distinguir un alta de una modificación sin él.
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

    /** Alta: la versión la lleva Spring Data JDBC a partir de aquí. */
    public static ObligadoTributario nuevo(UUID id, Nif nif, String nombreRazon, ZoneId zonaHoraria) {
        return new ObligadoTributario(id, nif, nombreRazon, zonaHoraria, 0);
    }
}
