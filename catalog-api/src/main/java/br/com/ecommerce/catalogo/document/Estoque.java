package br.com.ecommerce.catalogo.document;

// Embutido no produto: { disponivel: 10, reservado: 0 }.
// Reservado não é vendido: ao reservar (pedido.criado), disponivel diminui e reservado aumenta.
// Na próxima aula, o pagamento confirma a venda ou devolve ao disponível.
public record Estoque(int disponivel, int reservado) {

	public Estoque comDisponivel(int novoDisponivel) {
		return new Estoque(novoDisponivel, reservado);
	}

}
