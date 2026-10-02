package dev.lacre.remision;

import java.util.List;

/**
 * Los registros de una consulta a la AEAT.
 *
 * @param completo falso si se alcanzó el tope de páginas y la AEAT tenía más
 */
public record ResultadoConsulta(List<RegistroEnAeat> registros, boolean completo) {

    public ResultadoConsulta {
        registros = List.copyOf(registros);
    }
}
