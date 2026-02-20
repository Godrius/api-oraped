// src/main/java/br/com/oraped/repository/ClienteRepository.java
package br.com.oraped.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import br.com.oraped.domain.Cliente;
import br.com.oraped.domain.Estabelecimento;

public interface ClienteRepository extends JpaRepository<Cliente, Long> {

  Optional<Cliente> findByEstabelecimentoAndTelefone(Estabelecimento estabelecimento, String telefone);
}
