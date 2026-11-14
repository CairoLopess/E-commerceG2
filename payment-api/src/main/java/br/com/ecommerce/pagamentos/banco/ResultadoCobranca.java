package br.com.ecommerce.pagamentos.banco;

import br.com.ecommerce.pagamentos.entity.StatusPagamento;

/**
 * Como terminou a cobrança, depois de todas as tentativas.
 *
 * status: APROVADO, RECUSADO (o banco disse não) ou ERRO (o banco não respondeu).
 * houveTimeout: alguma tentativa ficou sem resposta. Num ERRO, quer dizer que o banco PODE ter
 * cobrado: quem chama pede o estorno da referência, por segurança.
 */
public record ResultadoCobranca(StatusPagamento status, String codigoTransacao, String motivo, int tentativas,
		boolean houveTimeout) {

	public boolean aprovado() {
		return status == StatusPagamento.APROVADO;
	}

}
