package com.ledger.security;

import java.io.IOException;
import java.util.List;

import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.ledger.model.User;
import com.ledger.model.User.UserStatus;
import com.ledger.repository.UserRepository;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

  private static final String BEARER_PREFIX = "Bearer ";

  private final JwtService jwtService;
  private final UserRepository userRepository;

  public JwtAuthenticationFilter(JwtService jwtService, UserRepository userRepository) {
    this.jwtService = jwtService;
    this.userRepository = userRepository;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request,
      HttpServletResponse response,
      FilterChain filterChain
  ) throws ServletException, IOException {
    try {
      String token = bearerToken(request.getHeader(HttpHeaders.AUTHORIZATION));
      if (token != null && SecurityContextHolder.getContext().getAuthentication() == null) {
        authenticate(token);
      }
      filterChain.doFilter(request, response);
    } finally {
      SecurityContextHolder.clearContext();
    }
  }

  private void authenticate(String token) {
    try {
      Claims claims = jwtService.parse(token);
      User user = findUser(claims);
      if (user == null || user.getStatus() != UserStatus.ACTIVE || user.getRole() == null) {
        return;
      }
      UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
          user,
          null,
          List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name()))
      );
      SecurityContextHolder.getContext().setAuthentication(authentication);
    } catch (JwtException | IllegalArgumentException exception) {
      SecurityContextHolder.clearContext();
    }
  }

  private User findUser(Claims claims) {
    Object uid = claims.get("uid");
    if (uid instanceof Number id) {
      return userRepository.findById(id.longValue()).orElse(null);
    }
    String email = claims.getSubject();
    if (email == null || email.isBlank()) {
      return null;
    }
    return userRepository.findByEmailIgnoreCase(email).orElse(null);
  }

  private String bearerToken(String header) {
    if (header == null || !header.startsWith(BEARER_PREFIX)) {
      return null;
    }
    String token = header.substring(BEARER_PREFIX.length()).trim();
    return token.isEmpty() ? null : token;
  }
}
