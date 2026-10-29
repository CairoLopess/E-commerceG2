package br.com.ecommerce.pedidos.dto;

import java.math.BigDecimal;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

// total: soma dos itens disponíveis. aviso: só aparece quando o catálogo não respondeu.
@JsonInclude(JsonInclude.Include.NON_NULL)
public record CarrinhoResponse(List<CarrinhoItemResponse> itens, BigDecimal total, String aviso) {
}
