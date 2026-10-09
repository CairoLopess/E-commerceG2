package br.com.ecommerce.usuarios.dto;

public record LoginResponse(String token, UsuarioResponse usuario) {
}
