package br.com.ecommerce.usuarios.config;

import java.io.IOException;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Rastreamento: o gateway dá um X-Request-Id a cada requisição (plugin correlation-id) e o
 * repassa ao serviço. Aqui o id entra no contexto de log (MDC), e toda linha escrita durante a
 * requisição sai com ele (logging.pattern.correlation). Com o mesmo id no log de todos os
 * serviços, um grep encontra a história inteira.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestIdFilter extends OncePerRequestFilter {

	public static final String HEADER = "X-Request-Id";
	public static final String MDC_KEY = "requestId";

	// O Kong mantém o id que o cliente mandar: só entra no log se for um id comum
	// (sem quebra de linha ou texto gigante para forjar linhas no log)
	private static final Pattern ID_VALIDO = Pattern.compile("[A-Za-z0-9._-]{1,64}");

	private static final Logger log = LoggerFactory.getLogger(RequestIdFilter.class);

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {
		// Sem o gateway na frente (rodando local), não há id: as linhas saem sem ele
		String requestId = request.getHeader(HEADER);
		if (requestId != null && ID_VALIDO.matcher(requestId).matches()) {
			MDC.put(MDC_KEY, requestId);
		}
		long inicio = System.nanoTime();
		try {
			chain.doFilter(request, response);
		} finally {
			long ms = (System.nanoTime() - inicio) / 1_000_000;
			// Uma linha por requisição: GET /usuarios/me -> 200 (12 ms)
			log.info("{} {} -> {} ({} ms)", request.getMethod(), request.getRequestURI(), response.getStatus(), ms);
			MDC.remove(MDC_KEY);
		}
	}

}
