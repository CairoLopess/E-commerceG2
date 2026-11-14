package br.com.ecommerce.catalogo.repository;

import java.util.Collection;
import java.util.Optional;

import br.com.ecommerce.catalogo.document.Reserva;

public interface ReservaRepositoryCustom {

	/**
	 * Muda o status da reserva só se ela estiver num dos status de origem, num update só
	 * (findAndModify com o status no filtro). Devolve a reserva como estava ANTES, ou vazio se não
	 * mudou: não existe, ou já saiu da origem (evento repetido, ou outro evento chegou antes).
	 * Quem recebe a reserva é o único que mexe no estoque: duas réplicas não devolvem duas vezes.
	 */
	Optional<Reserva> mudarStatus(String pedidoId, Collection<Reserva.Status> origens, Reserva.Status novo);

}
