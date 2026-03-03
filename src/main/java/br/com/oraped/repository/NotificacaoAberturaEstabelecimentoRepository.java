package br.com.oraped.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import br.com.oraped.domain.NotificacaoAberturaEstabelecimento;
import br.com.oraped.domain.enums.StatusNotificacaoAberturaEstabelecimento;

public interface NotificacaoAberturaEstabelecimentoRepository
    extends JpaRepository<NotificacaoAberturaEstabelecimento, Long> {

    Optional<NotificacaoAberturaEstabelecimento> findByIdEstabelecimentoAndWhatsappClienteAndStatus(
        Long idEstabelecimento,
        String whatsappCliente,
        StatusNotificacaoAberturaEstabelecimento status
    );

    List<NotificacaoAberturaEstabelecimento> findByIdEstabelecimentoAndStatus(
        Long idEstabelecimento,
        StatusNotificacaoAberturaEstabelecimento status
    );
}