/**
 * La puerta: quién puede llamar a {@code /v1/**}.
 * <p>
 * Una clave por despliegue, en {@code Authorization: Bearer}, y la aplicación no arranca sin
 * ella. El razonamiento de por qué una sola clave y por qué un filtro de servlet en vez de
 * Spring Security está en {@code PropiedadesApi} y {@code FiltroDeClaveDeApi}.
 * <p>
 * Es el único paquete de {@code api} que no tiene espejo en {@code verifactu}, y tiene sentido:
 * autenticar es asunto del despliegue, no del modelo fiscal, y por eso no viaja con la librería.
 */
package dev.lacre.api.internal.autenticacion;
