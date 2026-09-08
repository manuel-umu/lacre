-- Outbox de la remisión a la AEAT.
--
-- Se crea una fila en PENDIENTE en la MISMA transacción que el registro de facturación, con un
-- oyente síncrono (ADR 0004). Lo asíncrono es el despacho, que llega en la Fase 6: la norma
-- exige que el registro se genere al expedir la factura, no que la AEAT esté disponible.

create table envio_registro (
    id           uuid         primary key,
    registro_id  uuid         not null unique references registro_facturacion (id),
    estado       varchar(24)  not null,
    creado_en    timestamptz  not null,
    version      bigint       not null
);

comment on column envio_registro.registro_id is
    'UNIQUE: un registro se remite una vez. Si el ERP reintenta y se generase dos veces, la '
    'restricción lo para aquí antes de duplicar presentaciones ante la AEAT.';

-- A diferencia de registro_facturacion, esta tabla SÍ se modifica: el estado del envío cambia
-- con lo que responda la AEAT. Lo que no se hace nunca es borrar.
grant select, insert, update on envio_registro to lacre_app;
