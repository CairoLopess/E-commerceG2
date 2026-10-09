package br.com.ecommerce.catalogo.repository;

import java.util.List;
import java.util.regex.Pattern;

import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;

import br.com.ecommerce.catalogo.document.Produto;

// O Spring Data junta esta implementação ao ProdutoRepository pelo sufixo "Impl"
class ProdutoRepositoryCustomImpl implements ProdutoRepositoryCustom {

	private final MongoTemplate mongoTemplate;

	ProdutoRepositoryCustomImpl(MongoTemplate mongoTemplate) {
		this.mongoTemplate = mongoTemplate;
	}

	@Override
	public List<Produto> buscarAtivos(String categoria, String busca) {
		Criteria criteria = Criteria.where("ativo").is(true);
		if (categoria != null && !categoria.isBlank()) {
			criteria = criteria.and("categoria").is(categoria);
		}
		if (busca != null && !busca.isBlank()) {
			// Pattern.quote: o texto do usuário é literal, não vira expressão regular
			criteria = criteria.and("nome").regex(Pattern.quote(busca.trim()), "i");
		}
		Query query = Query.query(criteria).with(Sort.by("nome"));
		return mongoTemplate.find(query, Produto.class);
	}

}
