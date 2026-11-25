package br.com.ecommerce.catalogo.repository;

import java.util.Collection;
import java.util.Optional;

import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import br.com.ecommerce.catalogo.document.Reserva;

// O Spring Data junta esta implementação ao ReservaRepository pelo sufixo "Impl"
class ReservaRepositoryCustomImpl implements ReservaRepositoryCustom {

	private final MongoTemplate mongoTemplate;

	ReservaRepositoryCustomImpl(MongoTemplate mongoTemplate) {
		this.mongoTemplate = mongoTemplate;
	}

	@Override
	public Optional<Reserva> mudarStatus(String pedidoId, Collection<Reserva.Status> origens, Reserva.Status novo) {
		Query query = Query.query(Criteria.where("pedidoId").is(pedidoId).and("status").in(origens));
		Update update = new Update().set("status", novo);
		// returnNew(false): devolve como estava, para saber de qual status saiu
		return Optional.ofNullable(mongoTemplate.findAndModify(query, update,
				FindAndModifyOptions.options().returnNew(false), Reserva.class));
	}

}
