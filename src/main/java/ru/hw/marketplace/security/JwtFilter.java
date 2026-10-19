package ru.hw.marketplace.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.util.List;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import ru.hw.marketplace.exception.*;
import ru.hw.marketplace.generated.model.ErrorCode;

public class JwtFilter extends OncePerRequestFilter {
    private final JwtService jwt;
    private final ObjectMapper mapper;
    public JwtFilter(JwtService jwt, ObjectMapper mapper) { this.jwt = jwt; this.mapper = mapper; }
    @Override protected boolean shouldNotFilter(HttpServletRequest request) {
        return List.of("/auth/register", "/auth/login", "/auth/refresh").contains(request.getServletPath());
    }
    @Override protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain) throws ServletException, IOException {
        String header = req.getHeader("Authorization");
        if (header != null) {
            CurrentUser user;
            try {
                if (!header.startsWith("Bearer ")) throw new BusinessException(ErrorCode.TOKEN_INVALID, "Bearer token required");
                user = jwt.parse(header.substring(7), false);
            } catch (BusinessException e) {
                res.setStatus(Errors.status(e.code).value()); res.setContentType("application/json");
                mapper.writeValue(res.getOutputStream(), Errors.body(e.code, e.getMessage(), e.details));
                return;
            }
            req.setAttribute("user_id", user.id().toString());
            SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(user, null, List.of(new SimpleGrantedAuthority("ROLE_" + user.role().name()))));
        }
        chain.doFilter(req, res);
    }
}
