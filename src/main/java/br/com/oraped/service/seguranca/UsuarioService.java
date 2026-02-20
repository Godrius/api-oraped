package br.com.oraped.service.seguranca;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import br.com.oraped.domain.seguranca.Usuario;
import br.com.oraped.repository.seguranca.UsuarioRepository;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class UsuarioService {

  private final UsuarioRepository usuarioRepository;
  private final BCryptPasswordEncoder passwordEncoder;

  public Usuario buscarPorLogin(String login) {
    return usuarioRepository.findByLogin(login)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Usuário não encontrado"));
  }

  public Usuario buscarPorTokenId(String tokenId) {
    return usuarioRepository.findByTokenId(tokenId)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Usuário não encontrado"));
  }

  public Usuario inserir(Usuario usuario) {
    if (usuario.getId() != null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Novo usuário não pode ter ID.");
    }

    if (usuario.getLogin() == null || usuario.getLogin().isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "login é obrigatório.");
    }

    if (usuarioRepository.existsByLogin(usuario.getLogin())) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "login já cadastrado.");
    }

    if (usuario.getSenha() == null || usuario.getSenha().isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "senha é obrigatória.");
    }

    usuario.setSenha(passwordEncoder.encode(usuario.getSenha().trim()));
    usuario.setTokenId(gerarTokenUnico(32));
    usuario.setDataCriacao(LocalDateTime.now());

    if (usuario.getAtivado() == null) {
      usuario.setAtivado(true);
    }

    return usuarioRepository.save(usuario);
  }

  @Transactional
  public Usuario atualizar(Usuario usuario) {
    if (usuario.getId() == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "id é obrigatório.");
    }

    Usuario salvo = usuarioRepository.findById(usuario.getId())
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Usuário não encontrado."));

    // merge de campos (ajuste conforme seu domínio)
    if (usuario.getNome() != null) {
      salvo.setNome(usuario.getNome());
    }
    if (usuario.getLogin() != null) {
      salvo.setLogin(usuario.getLogin());
    }
    if (usuario.getPerfil() != null) {
      salvo.setPerfil(usuario.getPerfil());
    }
    if (usuario.getAtivado() != null) {
      salvo.setAtivado(usuario.getAtivado());
    }
    if (usuario.getDataUltimoAcesso() != null) {
      salvo.setDataUltimoAcesso(usuario.getDataUltimoAcesso());
    }

    // senha: só altera se vier senha NOVA (plaintext)
    if (usuario.getSenha() != null && !usuario.getSenha().isBlank()) {
      salvo.setSenha(passwordEncoder.encode(usuario.getSenha().trim()));
    }

    // tokenId: só altera se vier explícito
    if (usuario.getTokenId() != null && !usuario.getTokenId().isBlank()) {
      salvo.setTokenId(usuario.getTokenId());
    }

    return usuarioRepository.save(salvo);
  }

  @Transactional
  public void atualizarSenha(Usuario usuario, String senhaHash) {
    usuario.setSenha(senhaHash);
    usuarioRepository.save(usuario);
  }

  @Transactional
  public void atualizarDataUltimoAcesso(Long idUsuario, LocalDateTime data) {
    if (idUsuario == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "id é obrigatório.");
    }

    Usuario salvo = usuarioRepository.findById(idUsuario)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Usuário não encontrado."));

    salvo.setDataUltimoAcesso(data != null ? data : LocalDateTime.now());
    usuarioRepository.save(salvo);
  }

  private String gerarTokenUnico(int tamanho) {
    int tentativas = 0;
    while (tentativas < 10) {
      String token = gerarTokenSeguro(tamanho);
      if (!usuarioRepository.existsByTokenId(token)) {
        return token;
      }
      tentativas++;
    }
    throw new RuntimeException("Não foi possível gerar um token único.");
  }

  private String gerarTokenSeguro(int tamanho) {
    try {
      String uuid = UUID.randomUUID().toString();
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] hash = digest.digest(uuid.getBytes(StandardCharsets.UTF_8));
      StringBuilder hex = new StringBuilder();
      for (byte b : hash) {
        hex.append(String.format("%02x", b));
      }
      return "u_" + hex.substring(0, Math.min(tamanho, hex.length()));
    } catch (NoSuchAlgorithmException e) {
      throw new RuntimeException("Erro ao gerar token", e);
    }
  }
}
