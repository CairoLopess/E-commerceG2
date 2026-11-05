package br.com.ecommerce.pedidos.service;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import br.com.ecommerce.pedidos.catalogo.CatalogoClient;
import br.com.ecommerce.pedidos.catalogo.ProdutoCatalogo;
import br.com.ecommerce.pedidos.dto.CheckoutRequest;
import br.com.ecommerce.pedidos.dto.PedidoResponse;
import br.com.ecommerce.pedidos.entity.CarrinhoItem;
import br.com.ecommerce.pedidos.entity.ItemPedido;
import br.com.ecommerce.pedidos.entity.Pedido;
import br.com.ecommerce.pedidos.entity.StatusPedido;
import br.com.ecommerce.pedidos.evento.Eventos;
import br.com.ecommerce.pedidos.evento.KafkaConfig;
import br.com.ecommerce.pedidos.evento.PedidoCriado;
import br.com.ecommerce.pedidos.exception.ApiException;
import br.com.ecommerce.pedidos.repository.CarrinhoItemRepository;
import br.com.ecommerce.pedidos.repository.PedidoRepository;

@Service
public class PedidoService {

	private static final String NAO_ENCONTRADO = "Pedido não encontrado.";

	private static final Set<StatusPedido> CANCELAVEIS = Set.of(StatusPedido.AGUARDANDO_ESTOQUE,
			StatusPedido.AGUARDANDO_PAGAMENTO);

	private static final Logger log = LoggerFactory.getLogger(PedidoService.class);

	private final PedidoRepository pedidoRepository;
	private final CarrinhoItemRepository carrinhoRepository;
	private final CatalogoClient catalogo;
	private final TransactionTemplate transacao;
	private final Eventos eventos;

	public PedidoService(PedidoRepository pedidoRepository, CarrinhoItemRepository carrinhoRepository,
			CatalogoClient catalogo, TransactionTemplate transacao, Eventos eventos) {
		this.pedidoRepository = pedidoRepository;
		this.carrinhoRepository = carrinhoRepository;
		this.catalogo = catalogo;
		this.transacao = transacao;
		this.eventos = eventos;
	}

	/**
	 * Checkout: fecha o carrinho num pedido em AGUARDANDO_ESTOQUE e publica pedido.criado.
	 * Pergunta ao catálogo o preço de agora (catálogo fora: 503) e copia nome e preço para o
	 * pedido. O estoque não é conferido aqui: quem decide é o catálogo, ao reservar (evento).
	 */
	public PedidoResponse finalizar(UUID usuarioId, String clienteEmail, CheckoutRequest request, String requestId) {
		String cartaoFinal = cartaoFinal(request);

		List<CarrinhoItem> itens = carrinhoRepository.findByUsuarioIdOrderByProdutoId(usuarioId);
		if (itens.isEmpty()) {
			throw ApiException.invalido("Carrinho vazio.");
		}

		Map<String, ProdutoCatalogo> produtos = catalogo.buscarProdutos(CarrinhoService.ids(itens), requestId);

		Pedido pedido = new Pedido(usuarioId, clienteEmail, request.enderecoEntrega().normalizado(),
				request.formaPagamento(), cartaoFinal);
		for (CarrinhoItem item : itens) {
			ProdutoCatalogo produto = produtos.get(item.getProdutoId());
			if (produto == null) {
				throw ApiException.conflito("Produto indisponível: " + item.getProdutoId() + ".");
			}
			pedido.adicionarItem(new ItemPedido(produto.id(), produto.nome(), produto.preco(), item.getQuantidade()));
		}

		// Grava o pedido e esvazia o carrinho juntos. A chamada ao catálogo fica fora da
		// transação, para não segurar uma conexão do banco enquanto espera a resposta.
		transacao.executeWithoutResult(status -> {
			pedidoRepository.save(pedido);
			carrinhoRepository.deleteByUsuarioId(usuarioId);
		});

		// Só depois do COMMIT: nunca anuncie um pedido que pode sumir num rollback.
		// Se o Kafka cair entre o commit e o publicar, o pedido fica parado em AGUARDANDO_ESTOQUE:
		// é a escrita dupla. A solução é o padrão Outbox (limitação conhecida, ver README).
		try {
			eventos.publicar(KafkaConfig.TOPICO_PEDIDOS, "pedido.criado", pedido.getId().toString(),
					PedidoCriado.from(pedido));
		} catch (RuntimeException e) {
			log.error("Pedido {} gravado, mas o pedido.criado não foi publicado: ficará em AGUARDANDO_ESTOQUE",
					pedido.getId(), e);
		}
		return PedidoResponse.from(pedido);
	}

	@Transactional(readOnly = true)
	public List<PedidoResponse> listar(UUID usuarioId) {
		return pedidoRepository.findByUsuarioIdOrderByCriadoEmDesc(usuarioId).stream()
				.map(PedidoResponse::from)
				.toList();
	}

	@Transactional(readOnly = true)
	public PedidoResponse buscar(UUID usuarioId, UUID id) {
		return PedidoResponse.from(buscarDoUsuario(usuarioId, id));
	}

	/**
	 * Cancelar continua permitido em AGUARDANDO_ESTOQUE e AGUARDANDO_PAGAMENTO, com o mesmo UPDATE
	 * condicional dos eventos: se um evento mudou o status entre a leitura e o UPDATE, dá 409.
	 * Devolver o estoque de um pedido já reservado fica para a saga, na próxima aula.
	 */
	@Transactional
	public PedidoResponse cancelar(UUID usuarioId, UUID id) {
		Pedido pedido = buscarDoUsuario(usuarioId, id);
		if (!pedido.getStatus().podeCancelar()
				|| pedidoRepository.mudarStatus(id, StatusPedido.CANCELADO, CANCELAVEIS, "Cancelado pelo cliente.") == 0) {
			throw ApiException.conflito("Pedido com status " + pedido.getStatus() + " não pode ser cancelado.");
		}
		return PedidoResponse.from(buscarDoUsuario(usuarioId, id));
	}

	/**
	 * Reação a um evento: muda o status só se o pedido estiver num dos status de origem.
	 * Evento repetido ou atrasado não faz nada (fica no log).
	 */
	@Transactional
	public void mudarStatus(String pedidoId, StatusPedido novo, Collection<StatusPedido> origens, String motivo) {
		UUID id;
		try {
			id = UUID.fromString(pedidoId);
		} catch (IllegalArgumentException | NullPointerException e) {
			log.warn("Evento com pedidoId inválido ({}): ignorado", pedidoId);
			return;
		}
		if (pedidoRepository.mudarStatus(id, novo, origens, motivo) == 1) {
			log.info("Pedido {} agora está {}", id, novo);
		} else {
			log.info("Pedido {} não mudou para {}: não existe ou já saiu de {}", id, novo, origens);
		}
	}

	// O pedido de outra pessoa responde 404, não 403: um 403 confirmaria que o pedido existe
	private Pedido buscarDoUsuario(UUID usuarioId, UUID id) {
		return pedidoRepository.findByIdAndUsuarioId(id, usuarioId)
				.orElseThrow(() -> ApiException.naoEncontrado(NAO_ENCONTRADO));
	}

	private static String cartaoFinal(CheckoutRequest request) {
		if (!"cartao".equals(request.formaPagamento())) {
			return null;
		}
		String numero = request.numeroCartao();
		if (numero == null) {
			throw ApiException.invalido("numeroCartao é obrigatório para pagamento com cartão.");
		}
		return numero.substring(numero.length() - 4);
	}

}
