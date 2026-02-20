// src/main/java/br/com/oraped/service/AdministradorEstabelecimentoService.java
package br.com.oraped.service;

import java.util.List;
import java.util.Objects;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import br.com.oraped.domain.AdministradorEstabelecimento;
import br.com.oraped.repository.AdministradorEstabelecimentoRepository;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AdministradorEstabelecimentoService {

  private final AdministradorEstabelecimentoRepository administradorEstabelecimentoRepository;

  @Transactional(readOnly = true)
  public List<AdministradorEstabelecimento> listarAtivosPorEstabelecimento(Long idEstabelecimento) {

    if (idEstabelecimento == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "idEstabelecimento é obrigatório");
    }

    return administradorEstabelecimentoRepository.findByEstabelecimentoIdAndAtivoTrueOrderByNomeAsc(idEstabelecimento);
  }

  @Transactional(readOnly = true)
  public AdministradorEstabelecimento buscar(Long idAdministrador, Long idEstabelecimento) {

    if (idAdministrador == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "idAdministrador é obrigatório");
    }

    if (idEstabelecimento == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "idEstabelecimento é obrigatório");
    }

    AdministradorEstabelecimento admin = administradorEstabelecimentoRepository.findById(idAdministrador)
      .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Administrador não encontrado: " + idAdministrador));

    if (admin.getEstabelecimento() == null || !Objects.equals(admin.getEstabelecimento().getId(), idEstabelecimento)) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Administrador não pertence ao estabelecimento informado");
    }

    return admin;
  }
}
