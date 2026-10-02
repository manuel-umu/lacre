-- Un error de la AEAT que el operador ya ha resuelto, normalmente subsanando la factura con un
-- registro nuevo. Es una anotación de operación, no un estado de la remisión: el envío sigue
-- RECHAZADO o ACEPTADO_CON_ERRORES.

alter table envio_registro
    add column atendido_en   timestamptz,
    add column atendido_por  varchar(100);

comment on column envio_registro.atendido_en is
    'Cuándo marcó el operador este error como atendido. Solo en RECHAZADO y ACEPTADO_CON_ERRORES.';
