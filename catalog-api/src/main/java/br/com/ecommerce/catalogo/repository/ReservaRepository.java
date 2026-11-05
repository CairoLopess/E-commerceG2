package br.com.ecommerce.catalogo.repository;

import java.util.Optional;

import org.springframework.data.mongodb.repository.MongoRepository;

import br.com.ecommerce.catalogo.document.Reserva;

public interface ReservaRepository extends MongoRepository<Reserva, String> {

	Optional<Reserva> findByPedidoId(String pedidoId);

}
