package br.com.ecommerce.usuarios.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * O serviço não valida token nem perfil: isso é do gateway (kong/kong.yml).
 * Ele recebe a identidade pronta no cabeçalho X-User-Id (ver UsuarioLogado).
 */
@Configuration
public class SecurityConfig {

	@Bean
	PasswordEncoder passwordEncoder() {
		return new BCryptPasswordEncoder();
	}

}
