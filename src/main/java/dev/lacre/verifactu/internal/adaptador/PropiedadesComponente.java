package dev.lacre.verifactu.internal.adaptador;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuración del componente en cada despliegue: la identidad que declara y la zona horaria.
 * <p>
 * lacre es un <strong>componente de facturación (CF)</strong> que se integra en el componente
 * principal de facturación del cliente, según el art. 1.2 de la OM HAC/1177/2024.
 * <p>
 * Estos datos viajan en el bloque {@code SistemaInformatico} de cada registro y deben coincidir
 * con la declaración responsable que firme quien comercializa el sistema. Ese nombre
 * —{@code SistemaInformatico}, y los campos que lo componen— es el del esquema oficial de la
 * AEAT y se conserva tal cual: es terminología normativa, no nuestra.
 *
 * @param zonaHoraria zona con la que se fecha cada registro. <strong>Entra en el cálculo de la
 *                    huella</strong>, porque {@code FechaHoraHusoGenRegistro} se serializa con
 *                    su desplazamiento. Canarias no es {@code Europe/Madrid}: es
 *                    {@code Atlantic/Canary}, y usar la zona equivocada produce una huella que
 *                    la AEAT no reconoce.
 * @param multiObligado si el sistema puede dar soporte a la facturación de varios obligados.
 *                      Para un producto integrado en un ERP es que sí, y debe declararse igual
 *                      en el apartado 1.f) de la declaración responsable.
 * @param sirveAVariosObligados si esta instalación concreta lo está haciendo efectivamente
 */
@ConfigurationProperties("lacre.sistema-informatico")
public record PropiedadesComponente(
        String nombreRazon,
        String nif,
        String nombreSistemaInformatico,
        String idSistemaInformatico,
        String version,
        String numeroInstalacion,
        String zonaHoraria,
        boolean multiObligado,
        boolean sirveAVariosObligados) {

    public PropiedadesComponente {
        if (zonaHoraria == null || zonaHoraria.isBlank()) {
            zonaHoraria = "Europe/Madrid";
        }
    }
}
