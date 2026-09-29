package dev.lacre.verifactu.internal;

import static org.assertj.core.api.Assertions.assertThat;

import dev.lacre.shared.Huella;
import net.jqwik.api.Assume;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.CharRange;
import net.jqwik.api.constraints.IntRange;
import net.jqwik.api.constraints.StringLength;

/**
 * Los caracteres generados se limitan al rango anterior a los sustitutos (U+D800–U+DFFF): un
 * sustituto suelto no es codificable en UTF-8 y dos cadenas distintas darían la misma huella.
 */
class CalculadorHuellaProperties {

    @Property
    void esDeterminista(@ForAll @CharRange(from = ' ', to = '퟿') String cadena) {
        assertThat(CalculadorHuella.calcular(cadena)).isEqualTo(CalculadorHuella.calcular(cadena));
    }

    @Property
    void siempreProduceSesentaYCuatroHexEnMayusculas(@ForAll @CharRange(from = ' ', to = '퟿') String cadena) {
        Huella huella = CalculadorHuella.calcular(cadena);
        assertThat(huella.valor()).hasSize(64).matches("[0-9A-F]{64}");
    }

    @Property
    void cambiarUnCaracterCambiaLaHuella(
            @ForAll @CharRange(from = ' ', to = '퟿') @StringLength(min = 1, max = 200) String cadena,
            @ForAll @IntRange(min = 0, max = 199) int indice,
            @ForAll @CharRange(from = ' ', to = '퟿') char sustituto) {

        Assume.that(indice < cadena.length());
        Assume.that(cadena.charAt(indice) != sustituto);

        String mutada = cadena.substring(0, indice) + sustituto + cadena.substring(indice + 1);
        assertThat(CalculadorHuella.calcular(mutada)).isNotEqualTo(CalculadorHuella.calcular(cadena));
    }

    @Property
    void cadenasDistintasProducenHuellasDistintas(
            @ForAll @CharRange(from = ' ', to = '퟿') String una, @ForAll @CharRange(from = ' ', to = '퟿') String otra) {

        Assume.that(!una.equals(otra));
        assertThat(CalculadorHuella.calcular(una)).isNotEqualTo(CalculadorHuella.calcular(otra));
    }
}
