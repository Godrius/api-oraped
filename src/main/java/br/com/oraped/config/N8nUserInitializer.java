package br.com.oraped.config;

import java.time.LocalDateTime;

import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import br.com.oraped.domain.enums.PerfilUsuario;
import br.com.oraped.domain.seguranca.Usuario;
import br.com.oraped.repository.seguranca.UsuarioRepository;

@Configuration
public class N8nUserInitializer {

    @Bean
    CommandLineRunner initN8nUser(
        UsuarioRepository usuarioRepository,
        BCryptPasswordEncoder passwordEncoder
    ) {
        return args -> {

            String login   = "n8n";
            String tokenId = "u_n8n_oraped_01";
            String senha   = "OraPed@dmin";

            boolean existe = usuarioRepository.existsByLogin(login);

            if (!existe) {
                Usuario u = new Usuario();
                u.setNome("Integração N8N");
                u.setLogin(login);
                u.setTokenId(tokenId);
                u.setSenha(passwordEncoder.encode(senha));
                u.setPerfil(PerfilUsuario.INTEGRACAO_N8N);
                u.setAtivado(true);
                u.setDataCriacao(LocalDateTime.now());

                usuarioRepository.save(u);
            }
        };
    }
}
