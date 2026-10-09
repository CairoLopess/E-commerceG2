package br.com.ecommerce.usuarios.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import br.com.ecommerce.usuarios.entity.Endereco;

public interface EnderecoRepository extends JpaRepository<Endereco, UUID> {

	List<Endereco> findByUsuarioIdOrderByPrincipalDesc(UUID usuarioId);

	// Filtra pelo dono: o endereço de outro usuário se comporta como "não existe"
	Optional<Endereco> findByIdAndUsuarioId(UUID id, UUID usuarioId);

	@Modifying
	@Query("update Endereco e set e.principal = false where e.usuario.id = :usuarioId")
	void desmarcarPrincipal(UUID usuarioId);

}
