-- Idempotencia de la API (Fase 7.1).
--
-- Un ERP reintenta: por un timeout, por un reinicio, por un reintento automático de su cliente
-- HTTP. Sin esto, el segundo intento generaría un SEGUNDO registro en la cadena, con su propia
-- posición y su propia huella, y se presentarían dos veces los mismos datos a la AEAT.
--
-- La fila se escribe en la MISMA transacción que el registro, igual que el outbox: o existen
-- los dos o no existe ninguno. Si existiera el registro sin su anotación, el reintento
-- duplicaría igual.
--
-- Guarda la respuesta y no solo la clave. La alternativa —anotar la clave y releer después
-- registro_facturacion— obligaría a `api` a consultar la tabla del núcleo, el mismo
-- acoplamiento por nombre de tabla que ya se evitó con consulta.RegistrosRemitibles.

create table peticion_idempotente (
    obligado_id      uuid         not null references obligado (id),
    clave            varchar(128) not null,
    huella_peticion  char(64)     not null,
    registro_id      uuid         not null unique references registro_facturacion (id),
    posicion         bigint       not null,
    huella           char(64)     not null,
    creado_en        timestamptz  not null,
    primary key (obligado_id, clave)
);

comment on column peticion_idempotente.clave is
    'La cabecera Idempotency-Key tal y como la envió el integrador. El ámbito es POR OBLIGADO: '
    'dos ERP distintos, o dos obligados del mismo ERP, no se pisan las claves.';

comment on column peticion_idempotente.huella_peticion is
    'SHA-256 de la identidad fiscal de la factura pedida (emisor, número de serie, fecha de '
    'expedición e importe total). Si llega la misma clave con OTRA factura, se responde 409 en '
    'vez de devolver el registro equivocado: un ERP que creyera registrada la factura B cuando '
    'se registró la A perdería un registro sin enterarse.';

-- Como registro_facturacion, es de solo inserción: una respuesta ya dada no cambia.
grant select, insert on peticion_idempotente to lacre_app;
