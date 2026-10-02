package dev.lacre.remision;

/**
 * Situación de un registro respecto de su remisión a la AEAT. Solo {@link #PENDIENTE} y
 * {@link #APARTADO} no son terminales: un registro rechazado o aceptado con errores se subsana con
 * uno nuevo, no se reenvía.
 */
public enum EstadoEnvio {

    /** Creado junto al registro, todavía sin despachar. */
    PENDIENTE,

    /** La AEAT lo aceptó sin reparos: {@code EstadoRegistro} = {@code Correcto}. */
    ACEPTADO,

    /**
     * Aceptado y registrado por la AEAT con un error admisible (códigos 2000 a 2009) que hay que
     * subsanar.
     */
    ACEPTADO_CON_ERRORES,

    /**
     * Rechazado por la AEAT: {@code EstadoRegistro} = {@code Incorrecto}. Se subsana con un
     * registro nuevo.
     */
    RECHAZADO,

    /**
     * La AEAT respondió con el código 3000, «registro de facturación duplicado»: ya estaba
     * presentado.
     */
    DUPLICADO,

    /** Pendiente que el operador ha retirado del despacho; vuelve a {@link #PENDIENTE} al reanudarlo. */
    APARTADO;

    /** Código del catálogo de la AEAT que identifica el duplicado. */
    public static final int CODIGO_REGISTRO_DUPLICADO = 3000;

    public boolean esTerminal() {
        return this != PENDIENTE && this != APARTADO;
    }
}
