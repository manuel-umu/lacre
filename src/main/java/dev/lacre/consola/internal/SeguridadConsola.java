package dev.lacre.consola.internal;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Seguridad de {@code /consola/**}: inicio de sesión por formulario, sesión con CSRF y un único
 * usuario. El resto de rutas queda fuera de esta cadena.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(PropiedadesConsola.class)
class SeguridadConsola {

    @Bean
    SecurityFilterChain cadenaDeLaConsola(HttpSecurity http, PropiedadesConsola propiedades) throws Exception {
        http.securityMatcher("/consola/**");
        if (!propiedades.abierta()) {
            return http.authorizeHttpRequests(
                            peticiones -> peticiones.anyRequest().denyAll())
                    .exceptionHandling(
                            errores -> errores.authenticationEntryPoint((peticion, respuesta, e) -> noExiste(respuesta))
                                    .accessDeniedHandler((peticion, respuesta, e) -> noExiste(respuesta)))
                    .build();
        }
        return http.authorizeHttpRequests(peticiones -> peticiones
                        .requestMatchers(
                                "/consola/entrar", "/consola/consola.css", "/consola/htmx.min.js", "/consola/logo.svg")
                        .permitAll()
                        .anyRequest()
                        .authenticated())
                .formLogin(formulario -> formulario
                        .loginPage("/consola/entrar")
                        .defaultSuccessUrl("/consola", true)
                        .failureUrl("/consola/entrar?error"))
                .logout(salida -> salida.logoutUrl("/consola/salir").logoutSuccessUrl("/consola/entrar?salida"))
                .build();
    }

    @Bean
    PasswordEncoder codificadorDeClaves() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    /** Siempre hay uno, aunque vacío: sin él Spring Boot crearía un usuario con clave aleatoria. */
    @Bean
    UserDetailsService usuariosDeLaConsola(PropiedadesConsola propiedades, PasswordEncoder codificador) {
        if (!propiedades.abierta()) {
            return new InMemoryUserDetailsManager();
        }
        return new InMemoryUserDetailsManager(User.withUsername(propiedades.usuario())
                .password(codificador.encode(propiedades.clave()))
                .roles("OPERADOR")
                .build());
    }

    private static void noExiste(HttpServletResponse respuesta) throws java.io.IOException {
        respuesta.sendError(HttpServletResponse.SC_NOT_FOUND);
    }
}
