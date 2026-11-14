package br.com.ecommerce.catalogo.document;

// Embutido no produto: { disponivel: 10, reservado: 0 }.
// Reservado não é vendido: ao reservar (pedido.criado), disponivel diminui e reservado aumenta.
// Depois, a saga decide: pagamento aprovado confirma a venda (reservado diminui); recusado ou
// cancelado devolve ao disponível (disponivel aumenta, reservado diminui).
public record Estoque(int disponivel, int reservado) {

	public Estoque comDisponivel(int novoDisponivel) {
		return new Estoque(novoDisponivel, reservado);
	}

}
