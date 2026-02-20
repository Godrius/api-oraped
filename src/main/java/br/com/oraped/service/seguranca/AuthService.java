package br.com.oraped.service.seguranca;

import java.time.LocalDateTime;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import br.com.oraped.domain.seguranca.Usuario;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AuthService {

  private final UsuarioService usuarioService;
  private final BCryptPasswordEncoder passwordEncoder;

  public Usuario autenticar(String login, String senha) {
    Usuario usuario = usuarioService.buscarPorLogin(login);

    if (!Boolean.TRUE.equals(usuario.getAtivado())) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Não autorizado");
    }

    if (!passwordEncoder.matches(senha, usuario.getSenha())) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Não autorizado");
    }

    usuarioService.atualizarDataUltimoAcesso(usuario.getId(), LocalDateTime.now());

    // opcional: refletir o valor atualizado no objeto retornado
    usuario.setDataUltimoAcesso(LocalDateTime.now());

    return usuario;
  }

  public Usuario autenticarPorTokenId(String tokenId, String senha) {
    Usuario usuario = usuarioService.buscarPorTokenId(tokenId);

    if (!Boolean.TRUE.equals(usuario.getAtivado())) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Não autorizado");
    }

    if (!passwordEncoder.matches(senha, usuario.getSenha())) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Não autorizado");
    }

    usuarioService.atualizarDataUltimoAcesso(usuario.getId(), LocalDateTime.now());
    usuario.setDataUltimoAcesso(LocalDateTime.now());

    return usuario;
  }
}
