package br.com.oraped.repository.geolocalizacao;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import br.com.oraped.domain.geolocalizacao.EstabelecimentoBairroAtendido;

public interface EstabelecimentoBairroAtendidoRepository extends JpaRepository<EstabelecimentoBairroAtendido, Long> {

    boolean existsByEstabelecimentoIdAndBairroId(Long idEstabelecimento, Long idBairro);

    void deleteByEstabelecimentoIdAndBairroId(Long idEstabelecimento, Long idBairro);

    List<EstabelecimentoBairroAtendido> findByEstabelecimentoIdOrderByBairro_NomeAsc(Long idEstabelecimento);

    List<EstabelecimentoBairroAtendido> findByEstabelecimentoIdAndBairroIdIn(Long idEstabelecimento, List<Long> idsBairros);

    long countByEstabelecimentoId(Long idEstabelecimento);
}