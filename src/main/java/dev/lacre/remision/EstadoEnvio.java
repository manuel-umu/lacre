package dev.lacre.remision;

/**
 * Situación de un registro respecto de su remisión a la AEAT.
 * <p>
 * <strong>Solo {@link #PENDIENTE} no es terminal.</strong> Los otros cuatro cierran la vida de
 * la fila del outbox, incluidos los dos que traen error: ni un registro rechazado ni uno
 * aceptado con errores se reenvían, porque lo que la norma exige en ambos casos es
 * <em>subsanar</em>, y una subsanación es un registro de facturación <strong>nuevo</strong>, con
 * su propia fila. Reintentar aquí duplicaría presentaciones ante la AEAT.
 * <p>
 * Los fallos transitorios —la AEAT caída, un timeout— no aparecen: no son desenlaces, son
 * reintentos que dejan la fila en {@code PENDIENTE}. Quien los cuente será el despachador de la
 * Fase 6.3.
 */
public enum EstadoEnvio {

    /** Creado junto al registro, todavía sin despachar. Es el único estado del que se sale. */
    PENDIENTE,

    /** La AEAT lo aceptó sin reparos: {@code EstadoRegistro} = {@code Correcto}. */
    ACEPTADO,

    /**
     * Aceptado y registrado por la AEAT, pero con alguno de los errores admisibles del catálogo
     * —los códigos 2000 a 2009—, que hay que subsanar. El registro está presentado: tratarlo
     * como un fallo y reenviarlo sería duplicarlo.
     */
    ACEPTADO_CON_ERRORES,

    /**
     * La AEAT lo rechazó: {@code EstadoRegistro} = {@code Incorrecto}. No está registrado, y no
     * se arregla reenviando el mismo registro, sino generando uno nuevo subsanado.
     */
    RECHAZADO,

    /**
     * La AEAT contestó con el código <strong>3000</strong>, «registro de facturación duplicado»,
     * que es lo que devuelve reenviar algo ya presentado. No es un fallo: el registro está en la
     * AEAT. Existe como estado propio para que no se confunda con un error y se reintente
     * eternamente algo que ya está hecho.
     */
    DUPLICADO;

    /** Código del catálogo de la AEAT que identifica el duplicado. */
    public static final int CODIGO_REGISTRO_DUPLICADO = 3000;

    public boolean esTerminal() {
        return this != PENDIENTE;
    }
}
