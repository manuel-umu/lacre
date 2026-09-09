-- Lo que necesita el despachador de lotes de la Fase 6.3.
--
-- 1) El outbox pasa a saber de qué obligado es cada fila. Lo sabía registro_facturacion, pero
--    consultarlo en cada pasada del poller obligaría a `remision` a leer la tabla del núcleo, y
--    ese acoplamiento no lo detectaría ninguna regla ArchUnit: iría por el nombre de una tabla y
--    no por un import. El dato ya viaja en el evento RegistroCreado, así que no hay que ir a
--    buscarlo a ninguna parte.
--
-- 2) El contador de intentos que la 6.1 dejó fuera a propósito, por no tener quien lo
--    incrementara. Un fallo de remisión —timeout, red, respuesta ilegible— no es un desenlace:
--    deja la fila en PENDIENTE y suma un intento.
--
-- 3) El control de flujo del art. 16.2 de la OM HAC/1177/2024: el tiempo de espera entre envíos
--    empieza en 60 segundos, la AEAT lo actualiza en cada respuesta, y el siguiente envío sale
--    cuando pasan esos segundos DESDE EL ANTERIOR ENVÍO o cuando se acumulan 1000 registros, lo
--    que ocurra primero.

alter table envio_registro
    add column obligado_id  uuid references obligado (id),
    add column intentos     integer not null default 0;

-- Las filas que ya existan toman su obligado del registro al que apuntan. Después la columna
-- pasa a obligatoria: un envío sin obligado no se puede despachar, así que no debe poder existir.
update envio_registro v
set obligado_id = r.obligado_id
from registro_facturacion r
where r.id = v.registro_id
  and v.obligado_id is null;

alter table envio_registro alter column obligado_id set not null;

create index envio_registro_pendientes
    on envio_registro (obligado_id, creado_en)
    where estado = 'PENDIENTE';

comment on column envio_registro.intentos is
    'Veces que se ha intentado remitir sin obtener respuesta interpretable. No cuenta los '
    'desenlaces: un rechazo se responde a la primera y es terminal.';

-- La espera es del envío, no del registro: guardarla en las 1000 filas de un lote la duplicaría.
--
-- El ámbito es POR OBLIGADO y es una interpretación, no un hecho: el art. 16.2 habla del
-- "sistema informático", y lacre es un componente de facturación, no el SIF —el SIF es el ERP
-- del cliente más lacre—. Se elige por obligado porque cada uno presenta con SU PROPIO
-- certificado, así que ante la AEAT son autenticaciones distintas. Queda por confirmar contra el
-- Portal de Pruebas, junto con la otra pregunta abierta sobre qué identidad va en el bloque
-- SistemaInformatico: si la AEAT cuenta por lo que se declara ahí, el ámbito es eso.
create table control_flujo_envio (
    obligado_id       uuid         primary key references obligado (id),
    ultimo_envio      timestamptz,
    espera_segundos   integer      not null default 60,
    version           bigint       not null default 0
);

comment on table control_flujo_envio is
    'Control de flujo del art. 16.2 de la OM HAC/1177/2024. Una fila por obligado; ultimo_envio '
    'nulo significa que nunca se le ha remitido y puede salir de inmediato.';

grant select, insert, update on control_flujo_envio to lacre_app;
