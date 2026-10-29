package br.com.ecommerce.pedidos.service;

import java.util.List;
import java.util.Map;
import java.util.UUID;

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
import br.com.ecommerce.pedidos.exception.ApiException;
import br.com.ecommerce.pedidos.repository.CarrinhoItemRepository;
import br.com.ecommerce.pedidos.repository.PedidoRepository;

@Service
public class PedidoService {

	private static final String NAO_ENCONTRADO = "Pedido não encontrado.";

	private final PedidoRepository pedidoRepository;
	private final CarrinhoItemRepository carrinhoRepository;
	private final CatalogoClient catalogo;
	private final TransactionTemplate transacao;

	public PedidoService(PedidoRepository pedidoRepository, CarrinhoItemRepository carrinhoRepository,
			CatalogoClient catalogo, TransactionTemplate transacao) {
		this.pedidoRepository = pedidoRepository;
		this.carrinhoRepository = carrinhoRepository;
		this.catalogo = catalogo;
		this.transacao = transacao;
	}

	/**
	 * Checkout: fecha o carrinho num pedido. Pergunta ao catálogo o preço e o estoque de agora
	 * (catálogo fora: 503) e copia nome e preço para o pedido.
	 * O estoque só é conferido, não reservado: a reserva entra na aula 3, por evento.
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
			if (!produto.temEstoque(item.getQuantidade())) {
				throw ApiException.conflito("Estoque insuficiente para " + produto.nome() + ".");
			}
			pedido.adicionarItem(new ItemPedido(produto.id(), produto.nome(), produto.preco(), item.getQuantidade()));
		}

		// Grava o pedido e esvazia o carrinho juntos. A chamada ao catálogo fica fora da
		// transação, para não segurar uma conexão do banco enquanto espera a resposta.
		transacao.executeWithoutResult(status -> {
			pedidoRepository.save(pedido);
			carrinhoRepository.deleteByUsuarioId(usuarioId);
		});
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

	@Transactional
	public PedidoResponse cancelar(UUID usuarioId, UUID id) {
		Pedido pedido = buscarDoUsuario(usuarioId, id);
		if (!pedido.getStatus().podeCancelar()) {
			throw ApiException.conflito("Pedido com status " + pedido.getStatus() + " não pode ser cancelado.");
		}
		pedido.cancelar();
		return PedidoResponse.from(pedido);
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
