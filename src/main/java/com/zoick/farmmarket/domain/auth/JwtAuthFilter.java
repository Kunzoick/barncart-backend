package com.zoick.farmmarket.domain.auth;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;

@Component
@RequiredArgsConstructor
/*
Intercepts every HTTP request. Extracts the Bearer token from the Authorization header, validates it,
loads the user, and sets the security context so spring security knows who is making the request.
 */
public class JwtAuthFilter extends OncePerRequestFilter{
    private final JwtUtil jwtUtil;
    private final CustomUserDetailsService userDetailsService;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        String authheader= request.getHeader("Authorization");
        if(authheader == null || !authheader.startsWith("Bearer ")){
            filterChain.doFilter(request, response);
            return;
        }
        String token= authheader.substring(7);
        if(!jwtUtil.isAccessTokenValid(token)){
            filterChain.doFilter(request, response);
            return;
        }
        String email= jwtUtil.extractEmail(token);
        //only set context if not already authenticated
        if(email != null && SecurityContextHolder.getContext().getAuthentication() == null){
            UserDetails userDetails= userDetailsService.loadUserByUsername(email);
            UsernamePasswordAuthenticationToken authToken= new UsernamePasswordAuthenticationToken(userDetails,
                    null, userDetails.getAuthorities());
            authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
            SecurityContextHolder.getContext().setAuthentication(authToken);
        }
        filterChain.doFilter(request, response);
    }
}
