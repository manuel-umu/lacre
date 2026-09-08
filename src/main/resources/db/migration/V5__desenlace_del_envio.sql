-- Desenlace de la remisión a la AEAT.
--
-- envio_registro nació en V4 con el estado inicial y poco más. Aquí gana lo que hace falta para
-- dejar constancia de en qué acabó: cuándo se remitió y, si la AEAT devolvió un error, cuál.
--
-- No hay contador de intentos: los fallos transitorios —AEAT caída, timeout— no son estados de
-- este agregado, y quien los cuente será el despachador de la Fase 6.3. La columna se añade
-- cuando exista quien la incremente; esta tabla sí se puede modificar, al contrario que
-- registro_facturacion.

alter table envio_registro
    add column enviado_en         timestamptz,
    add column codigo_error       integer,
    add column descripcion_error  varchar(500);

comment on column envio_registro.enviado_en is
    'Momento en que la AEAT respondió, no en que se intentó. Nulo mientras está PENDIENTE.';

comment on column envio_registro.codigo_error is
    'Código del catálogo de la AEAT (errores.properties). Se guarda también cuando el registro '
    'queda ACEPTADO_CON_ERRORES, porque ahí hay algo que subsanar.';
