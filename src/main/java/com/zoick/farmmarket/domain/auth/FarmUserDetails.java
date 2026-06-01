package com.zoick.farmmarket.domain.auth;
import com.zoick.farmmarket.domain.user.Role;
import com.zoick.farmmarket.domain.user.User;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Getter
//stores only what we need from the User entity at load time, No JPA reference
public class FarmUserDetails implements UserDetails{
    private final UUID userId;
    private final String email;
    private final String firstName;
    private final String passwordHash;
    private final Role role;
    private final boolean active;

    //built from user entity at load time
    public FarmUserDetails(User user){
        this.userId= user.getId();
        this.email= user.getEmail();
        this.firstName= user.getFirstName();
        this.passwordHash= user.getPasswordHash();
        this.role= user.getRole();
        this.active= user.isActive();
    }
    @Override
    public Collection<? extends GrantedAuthority> getAuthorities(){
        return List.of(new SimpleGrantedAuthority("ROLE_"+ role.name()));
    }
    @Override
    public String getPassword(){
        return passwordHash;
    }
    @Override
    public String getUsername(){
        return email;
    }
    @Override
    public boolean isEnabled(){
        return active;
    }
    @Override
    public boolean isAccountNonExpired(){
        return true;
    }
    @Override
    public boolean isAccountNonLocked(){
        return true;
    }
    @Override
    public boolean isCredentialsNonExpired(){
        return true;
    }
}
