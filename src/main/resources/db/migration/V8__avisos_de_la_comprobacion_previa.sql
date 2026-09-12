-- Los avisos del art. 7.i que se devolvieron con el registro (Fase 7.3).
--
-- Se anotan aquí, y no en registro_facturacion, porque no son parte del registro fiscal sino de
-- la respuesta que se dio: un reintento con la misma clave de idempotencia tiene que devolver la
-- misma, y una respuesta sin avisos afirmaría que la cadena estaba sana cuando no lo estaba.

alter table peticion_idempotente add column avisos varchar(255);

comment on column peticion_idempotente.avisos is
    'Códigos de las anomalías que la comprobación previa del art. 7.i encontró al registrar, '
    'separados por comas. Nulo si no hubo ninguna.';
