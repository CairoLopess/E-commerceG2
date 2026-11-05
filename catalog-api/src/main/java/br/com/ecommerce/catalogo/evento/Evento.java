package br.com.ecommerce.catalogo.evento;

import tools.jackson.databind.JsonNode;

/**
 * O envelope de todo evento, igual em todos os serviços:
 * { id, tipo, pedidoId, ocorridoEm, correlationId, dados }.
 *
 * id: único por evento (para quem quiser descartar repetidos). tipo: "pedido.criado".
 * pedidoId: também é a chave da mensagem (ordem por pedido). ocorridoEm: quando o fato
 * aconteceu, não quando foi lido. correlationId: o X-Request-Id do Kong, que segue de evento em
 * evento. dados: o conteúdo, diferente para cada tipo.
 */
public record Evento(String id, String tipo, String pedidoId, String ocorridoEm, String correlationId,
		JsonNode dados) {
}
