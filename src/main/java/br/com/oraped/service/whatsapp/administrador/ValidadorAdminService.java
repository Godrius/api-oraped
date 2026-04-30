package br.com.oraped.service.whatsapp.administrador;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import br.com.oraped.domain.AdministradorEstabelecimento;
import br.com.oraped.domain.Estabelecimento;
import br.com.oraped.service.AdministradorEstabelecimentoService;
import br.com.oraped.service.whatsapp.administrador.utils.AdminWhatsappUiHelper;
import lombok.RequiredArgsConstructor;

/**
 * Responsável por validar e identificar administradores ativos de um estabelecimento.
 *
 * Aplicação:
 * - Utilizado pelo fluxo administrativo do WhatsApp para garantir que apenas admins autorizados executem ações.
 *
 * Observação:
 * - Não trata níveis de permissão, apenas validação básica de pertencimento e status ativo.
 */
@Service
@RequiredArgsConstructor
public class ValidadorAdminService {

    private final AdministradorEstabelecimentoService administradorEstabelecimentoService;
    private final AdminWhatsappUiHelper sup;

    public List<String> listarWhatsappsAdministradoresAtivos(Estabelecimento estabelecimento) {

        if (estabelecimento == null || estabelecimento.getId() == null) {
            return List.of();
        }

        return administradorEstabelecimentoService
            .listarAtivosPorEstabelecimento(estabelecimento.getId())
            .stream()
            .filter(Objects::nonNull)
            .filter(AdministradorEstabelecimento::isAtivo)
            .map(AdministradorEstabelecimento::getWhatsapp)
            .filter(Objects::nonNull)
            .map(String::trim)
            .filter(StringUtils::hasText)
            // Normaliza para evitar divergência de formato (DDD, +55, etc.)
            .map(sup.msg()::normalizarSomenteDigitos)
            .filter(StringUtils::hasText)
            .distinct()
            .collect(Collectors.toList());
    }

    public boolean isAdminAtivo(Estabelecimento estabelecimento, String whatsapp) {

        if (estabelecimento == null || estabelecimento.getId() == null) {
            return false;
        }

        String whatsappNormalizado = sup.msg().normalizarSomenteDigitos(whatsapp);

        if (!StringUtils.hasText(whatsappNormalizado)) {
            return false;
        }

        return listarWhatsappsAdministradoresAtivos(estabelecimento)
            .stream()
            .anyMatch(whatsappNormalizado::equals);
    }
}