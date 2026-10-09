package br.com.ecommerce.usuarios.seed;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import br.com.ecommerce.usuarios.entity.Endereco;
import br.com.ecommerce.usuarios.entity.Perfil;
import br.com.ecommerce.usuarios.entity.Usuario;
import br.com.ecommerce.usuarios.repository.EnderecoRepository;
import br.com.ecommerce.usuarios.repository.UsuarioRepository;

/**
 * Seed obrigatório: admin@loja.com / admin123 e cliente@loja.com / cliente123 (com um endereço).
 * Idempotente: só cria o que ainda não existe, então rodar de novo não duplica.
 */
@Component
public class DataSeeder implements ApplicationRunner {

	private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);

	private final UsuarioRepository usuarioRepository;
	private final EnderecoRepository enderecoRepository;
	private final PasswordEncoder passwordEncoder;

	public DataSeeder(UsuarioRepository usuarioRepository, EnderecoRepository enderecoRepository,
			PasswordEncoder passwordEncoder) {
		this.usuarioRepository = usuarioRepository;
		this.enderecoRepository = enderecoRepository;
		this.passwordEncoder = passwordEncoder;
	}

	@Override
	@Transactional
	public void run(ApplicationArguments args) {
		if (!usuarioRepository.existsByEmail("admin@loja.com")) {
			usuarioRepository.save(new Usuario("Administrador", "admin@loja.com",
					passwordEncoder.encode("admin123"), Perfil.ADMIN));
			log.info("Seed: admin@loja.com criado");
		}

		if (!usuarioRepository.existsByEmail("cliente@loja.com")) {
			Usuario cliente = usuarioRepository.save(new Usuario("Cliente Exemplo", "cliente@loja.com",
					passwordEncoder.encode("cliente123"), Perfil.CLIENTE));
			enderecoRepository.save(new Endereco(cliente, "Casa", "01310-100", "Avenida Paulista", "1000",
					"Apto 101", "São Paulo", "SP", true));
			log.info("Seed: cliente@loja.com criado com um endereço");
		}
	}

}
