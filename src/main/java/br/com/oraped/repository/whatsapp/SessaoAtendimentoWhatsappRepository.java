package br.com.oraped.repository.whatsapp;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import br.com.oraped.domain.whatsapp.SessaoAtendimentoWhatsapp;

public interface SessaoAtendimentoWhatsappRepository extends JpaRepository<SessaoAtendimentoWhatsapp, Long> {

    @Query("""
        select s
        from SessaoAtendimentoWhatsapp s
        where s.whatsappCliente = :whatsappCliente
          and s.whatsappReceptor = :whatsappReceptor
          and s.encerradaEm is null
        order by s.ultimaInteracaoEm desc
    """)
    List<SessaoAtendimentoWhatsapp> buscarSessoesAtivas(
        @Param("whatsappCliente") String whatsappCliente,
        @Param("whatsappReceptor") String whatsappReceptor,
        Pageable pageable
    );

    default Optional<SessaoAtendimentoWhatsapp> buscarSessaoAtiva(String whatsappCliente, String whatsappReceptor) {
        List<SessaoAtendimentoWhatsapp> list = buscarSessoesAtivas(
            whatsappCliente,
            whatsappReceptor,
            Pageable.ofSize(1)
        );
        return (list == null || list.isEmpty()) ? Optional.empty() : Optional.of(list.get(0));
    }
}