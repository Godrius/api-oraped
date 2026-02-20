package br.com.oraped.repository.seguranca;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import br.com.oraped.domain.seguranca.Usuario;

public interface UsuarioRepository extends JpaRepository<Usuario, Long> {

  Optional<Usuario> findByLogin(String login);

  Optional<Usuario> findByTokenId(String tokenId);

  boolean existsByLogin(String login);

  boolean existsByTokenId(String tokenId);
}
