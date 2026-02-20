// src/main/java/br/com/oraped/repository/AdministradorEstabelecimentoRepository.java
package br.com.oraped.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import br.com.oraped.domain.AdministradorEstabelecimento;

public interface AdministradorEstabelecimentoRepository extends JpaRepository<AdministradorEstabelecimento, Long> {

  List<AdministradorEstabelecimento> findByEstabelecimentoIdAndAtivoTrueOrderByNomeAsc(Long idEstabelecimento);

  boolean existsByEstabelecimentoIdAndWhatsapp(Long idEstabelecimento, String whatsapp);
}
