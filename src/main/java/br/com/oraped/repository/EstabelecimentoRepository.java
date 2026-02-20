// src/main/java/br/com/oraped/repository/EstabelecimentoRepository.java
package br.com.oraped.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import br.com.oraped.domain.Estabelecimento;

public interface EstabelecimentoRepository extends JpaRepository<Estabelecimento, Long> {

	boolean existsByWhatsapp(String whatsapp);

	Optional<Estabelecimento> findByWhatsapp(String whatsapp);
	  
  
}
