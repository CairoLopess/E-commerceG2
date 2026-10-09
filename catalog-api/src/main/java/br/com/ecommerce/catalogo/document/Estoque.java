package br.com.ecommerce.catalogo.document;

// Embutido no produto: { disponivel: 10, reservado: 0 }.
// "reservado" passa a ser usado na aula 3, quando o pedido reserva estoque.
public record Estoque(int disponivel, int reservado) {

	public Estoque comDisponivel(int novoDisponivel) {
		return new Estoque(novoDisponivel, reservado);
	}

}
