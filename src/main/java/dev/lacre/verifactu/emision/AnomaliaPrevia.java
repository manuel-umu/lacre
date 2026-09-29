package dev.lacre.verifactu.emision;

/**
 * Anomalía que la comprobación previa del art. 7.i de la OM HAC/1177/2024 encuentra en el
 * registro que precede al que se emite. No impide emitir: la facturación no debe interrumpirse.
 */
public enum AnomaliaPrevia {
    HUELLA_ANTERIOR_NO_CUADRA("El registro que precede al emitido declara como huella anterior una que no es la "
            + "del registro que le precede a él. La cadena está rota por debajo del "
            + "registro nuevo, que sí ha quedado encadenado."),

    IDENTIFICACION_ANTERIOR_NO_CUADRA(
            "El registro que precede al emitido declara como anterior una factura que no es la "
                    + "del registro que le precede a él, o su XML no se puede leer. La cadena "
                    + "está rota por debajo del registro nuevo, que sí ha quedado encadenado."),

    FECHA_DEL_ANTERIOR_EN_EL_FUTURO(
            "El registro que precede al emitido se generó con más de un minuto de adelanto " + "sobre la hora actual.");

    private final String mensaje;

    AnomaliaPrevia(String mensaje) {
        this.mensaje = mensaje;
    }

    /** Descripción para quien integra, que no ve el log del despliegue. */
    public String mensaje() {
        return mensaje;
    }
}
