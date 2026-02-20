package br.com.oraped.security;

import java.io.IOException;
import java.util.Set;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import br.com.oraped.domain.seguranca.Usuario;
import br.com.oraped.repository.seguranca.UsuarioRepository;
import br.com.oraped.service.seguranca.AuthTokenService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class SecurityFilter extends OncePerRequestFilter {

  private final AuthTokenService tokenService;
  private final UsuarioRepository usuarioRepository;

  // Rotas públicas (sem JWT)
  private static final Set<String> PUBLIC_EXACT = Set.of(
    "/health",
    "/auth/login",
    "/auth/login-por-token",
    "/actuator/health"
  );

  @Override
  protected boolean shouldNotFilter(HttpServletRequest req) {
    String p = req.getServletPath();
    return PUBLIC_EXACT.contains(p) || p.startsWith("/actuator/health/");
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request,
      HttpServletResponse response,
      FilterChain filterChain
  ) throws ServletException, IOException {

    try {
      String authHeader = request.getHeader(HttpHeaders.AUTHORIZATION);

      // Sem Bearer: não autentica, só segue (o SecurityConfig decide 401/403 quando necessário)
      if (authHeader == null || authHeader.isBlank() || !authHeader.startsWith("Bearer ")) {
        filterChain.doFilter(request, response);
        return;
      }

      String token = authHeader.substring("Bearer ".length()).trim();
      if (token.isEmpty()) {
        unauthorizedJson(response, "{\"error\":\"unauthorized\"}");
        return;
      }

      String login = tokenService.validarTokenAutenticacao(token);

      Usuario usuario = usuarioRepository.findByLogin(login).orElse(null);
      if (usuario == null) {
        unauthorizedJson(response, "{\"error\":\"unauthorized\"}");
        return;
      }

      var authentication = new UsernamePasswordAuthenticationToken(
        usuario, null, usuario.getAuthorities()
      );
      authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
      SecurityContextHolder.getContext().setAuthentication(authentication);

      filterChain.doFilter(request, response);

    } catch (com.auth0.jwt.exceptions.TokenExpiredException e) {
      unauthorizedJson(response, "{\"error\":\"token_expired\"}");
    } catch (Exception e) {
      // Não estoure 500 por causa de token inválido / parsing / qualquer falha
      logger.warn("Erro no filtro de segurança", e);
      unauthorizedJson(response, "{\"error\":\"unauthorized\"}");
    }
  }

  private void unauthorizedJson(HttpServletResponse response, String body) throws IOException {
    if (response.isCommitted()) {
      return;
    }
    response.resetBuffer();
    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
    response.getWriter().write(body);
    response.flushBuffer();
  }
}
