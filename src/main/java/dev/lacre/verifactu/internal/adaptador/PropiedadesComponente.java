package dev.lacre.verifactu.internal.adaptador;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Identidad del componente que viaja en el bloque {@code SistemaInformatico} de cada registro;
 * debe coincidir con la declaración responsable.
 *
 * @param multiObligado si el sistema puede dar servicio a varios obligados
 * @param sirveAVariosObligados si esta instalación lo hace efectivamente
 */
@ConfigurationProperties("lacre.sistema-informatico")
public record PropiedadesComponente(
        String nombreRazon,
        String nif,
        String nombreSistemaInformatico,
        String idSistemaInformatico,
        String version,
        String numeroInstalacion,
        boolean multiObligado,
        boolean sirveAVariosObligados) {}
