package com.zoick.farmmarket.domain.auth;
import com.zoick.farmmarket.domain.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
@RequiredArgsConstructor
/*
Bridges your User entity and Spring Security. Spring Security calls loadUserByUsername during authentication
we load the user by email and wrap it in a UserDetails object Spring security understands
 */
public class CustomUserDetailsService implements UserDetailsService{
    private final UserRepository userRepository;
    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        var user= userRepository.findByEmail(email).orElseThrow(() -> new UsernameNotFoundException("User not found with email: "+ email));
        return new FarmUserDetails(user);
    }
}
