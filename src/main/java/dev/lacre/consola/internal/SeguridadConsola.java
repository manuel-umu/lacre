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
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;

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
                        .requestMatchers("/consola/entrar", "/consola/consola.css", "/consola/htmx.min.js")
                        .permitAll()
                        .anyRequest()
                        .authenticated())
                .formLogin(formulario -> formulario
                        .loginPage("/consola/entrar")
                        .defaultSuccessUrl("/consola", true)
                        .failureUrl("/consola/entrar?error"))
                .logout(salida -> salida.logoutUrl("/consola/salir").logoutSuccessUrl("/consola/entrar?salida"))
                .exceptionHandling(errores -> errores.authenticationEntryPoint(aLaEntrada()))
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

    /**
     * Sin sesión, a la página de entrada: por redirección o, a una petición de htmx, con
     * {@code HX-Redirect}, que lleva la ventana entera y no solo el fragmento.
     */
    private static AuthenticationEntryPoint aLaEntrada() {
        AuthenticationEntryPoint redireccion = new LoginUrlAuthenticationEntryPoint("/consola/entrar");
        return (peticion, respuesta, e) -> {
            if (peticion.getHeader("HX-Request") == null) {
                redireccion.commence(peticion, respuesta, e);
                return;
            }
            respuesta.setHeader("HX-Redirect", peticion.getContextPath() + "/consola/entrar");
            respuesta.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        };
    }

    private static void noExiste(HttpServletResponse respuesta) throws java.io.IOException {
        respuesta.sendError(HttpServletResponse.SC_NOT_FOUND);
    }
}
