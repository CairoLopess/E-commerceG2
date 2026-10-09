package br.com.ecommerce.usuarios.exception;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Converte toda exceção no formato { "erro": ..., "detalhes": [...] }.
 * Nunca devolve stack trace nem mensagem do banco.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

	private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

	@ExceptionHandler(ApiException.class)
	public ResponseEntity<ErroResponse> handleApi(ApiException ex) {
		return ResponseEntity.status(ex.getStatus()).body(new ErroResponse(ex.getMessage()));
	}

	// 401 e 403 vindos do Spring Security (repassados pelo SecurityErrorHandler)
	@ExceptionHandler(AuthenticationException.class)
	public ResponseEntity<ErroResponse> handleAuthentication(AuthenticationException ex) {
		return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
				.body(new ErroResponse("Token ausente, inválido ou vencido."));
	}

	@ExceptionHandler(AccessDeniedException.class)
	public ResponseEntity<ErroResponse> handleAccessDenied(AccessDeniedException ex) {
		return ResponseEntity.status(HttpStatus.FORBIDDEN)
				.body(new ErroResponse("Você não tem permissão para acessar este recurso."));
	}

	// O banco é a última linha de defesa (UNIQUE, NOT NULL)
	@ExceptionHandler(DataIntegrityViolationException.class)
	public ResponseEntity<ErroResponse> handleDataIntegrity(DataIntegrityViolationException ex) {
		log.warn("Violação de integridade: {}", ex.getMostSpecificCause().getMessage());
		return ResponseEntity.status(HttpStatus.CONFLICT)
				.body(new ErroResponse("Os dados conflitam com um registro existente."));
	}

	@ExceptionHandler(Exception.class)
	public ResponseEntity<ErroResponse> handleInesperado(Exception ex) {
		log.error("Erro inesperado", ex);
		return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new ErroResponse("Erro interno."));
	}

	@Override
	protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
			HttpHeaders headers, HttpStatusCode status, WebRequest request) {
		List<String> detalhes = ex.getBindingResult().getFieldErrors().stream()
				.map(erro -> erro.getDefaultMessage())
				.sorted()
				.toList();
		return ResponseEntity.badRequest().body(new ErroResponse("Dados inválidos.", detalhes));
	}

	// Demais erros do Spring MVC (JSON malformado, rota inexistente, método errado...)
	@Override
	protected ResponseEntity<Object> handleExceptionInternal(Exception ex, Object body, HttpHeaders headers,
			HttpStatusCode statusCode, WebRequest request) {
		String mensagem = switch (statusCode.value()) {
			case 400 -> "Requisição inválida.";
			case 404 -> "Recurso não encontrado.";
			case 405 -> "Método não permitido para esta rota.";
			case 415 -> "Content-Type não suportado. Use application/json.";
			default -> "Erro ao processar a requisição.";
		};
		return ResponseEntity.status(statusCode).headers(headers).body(new ErroResponse(mensagem));
	}

}
