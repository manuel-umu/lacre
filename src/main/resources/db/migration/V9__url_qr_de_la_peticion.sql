-- La URL del código QR que se devolvió con el registro (Fase 7.4).
--
-- Se anota por lo mismo que los avisos: el reintento con la misma clave devuelve la respuesta
-- guardada, y el ERP la necesita para imprimir la factura. Recalcularla en el reintento la haría
-- depender de lo que traiga esa segunda petición, que no tiene por qué llevar el mismo importe:
-- la huella de idempotencia identifica la factura, no su contenido entero.

alter table peticion_idempotente add column url_qr varchar(512);

comment on column peticion_idempotente.url_qr is
    'URL del servicio de cotejo de la AEAT que va dentro del código QR. Nula en las anulaciones, '
    'que no se imprimen.';
