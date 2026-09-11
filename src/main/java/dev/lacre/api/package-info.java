/**
 * La API REST: el producto que se integra en el ERP del cliente.
 * <p>
 * Es el único módulo que mira a los otros tres. Traduce el contrato que se vende —versionado, y
 * que por tanto no puede moverse cuando se mueve el dominio— a los casos de uso publicados de
 * {@code verifactu} e {@code identidad}, y no toca la base de datos de nadie salvo la suya, la
 * de idempotencia.
 * <p>
 * No hay nada en el paquete raíz y no debería haberlo: este módulo no publica API para los
 * demás, la publica para fuera del proceso.
 */
package dev.lacre.api;
