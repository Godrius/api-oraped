package br.com.oraped.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import br.com.oraped.domain.Estabelecimento;
import br.com.oraped.domain.enums.AbrangenciaEntrega;

public interface EstabelecimentoRepository extends JpaRepository<Estabelecimento, Long> {

    boolean existsByWhatsapp(String whatsapp);

    Optional<Estabelecimento> findByWhatsapp(String whatsapp);

    Optional<Estabelecimento> findByWhatsappAndAtivoTrue(String whatsapp);

    @Query("""
        select distinct e
        from Estabelecimento e
        left join e.bairro bairroBase
        left join e.bairrosAtendidos bairrosAtendidos
        left join bairrosAtendidos.bairro bairroAtendido
        where e.ativo = true
          and e.aberto = true
          and e.categoriaMarketplace.id = :idCategoria
          and (
                e.abrangenciaEntrega = :abrangenciaNacional
                or (
                    e.abrangenciaEntrega = :abrangenciaEstado
                    and upper(bairroBase.uf) = upper(:uf)
                )
                or (
                    e.abrangenciaEntrega = :abrangenciaBairro
                    and bairroAtendido.nomeNormalizado = :bairroNormalizado
                    and upper(bairroAtendido.uf) = upper(:uf)
                )
          )
        order by e.nome asc
    """)
    List<Estabelecimento> listarDisponiveisMarketplacePorCategoriaERegiao(
        Long idCategoria,
        AbrangenciaEntrega abrangenciaNacional,
        AbrangenciaEntrega abrangenciaEstado,
        AbrangenciaEntrega abrangenciaBairro,
        String uf,
        String bairroNormalizado
    );
}