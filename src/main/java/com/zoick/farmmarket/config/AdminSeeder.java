package com.zoick.farmmarket.config;
import com.zoick.farmmarket.domain.user.Role;
import com.zoick.farmmarket.domain.user.User;
import com.zoick.farmmarket.domain.user.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
//runs on every startup.Creates the admin user from environment variables
//if it does not already exist. fails loud if ADMIN_PASSWORD is not set
public class AdminSeeder implements ApplicationRunner{
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    @Value("${admin.email:admin@farmmarket.com}")
    private String adminEmail;
    //no default
    @Value("${admin.password}")
    private String adminPassword;
    @Override
    public void run(ApplicationArguments args){
        if(userRepository.existsByEmail(adminEmail)){
            log.info("Admin user already exists- skipping seed");
            return;
        }
        User admin= new User();
        admin.setEmail(adminEmail);
        admin.setPasswordHash(passwordEncoder.encode(adminPassword));
        admin.setFirstName("Farm");
        admin.setLastName("Admin");
        admin.setRole(Role.ADMIN);
        admin.setActive(true);
        userRepository.save(admin);
        log.info("Admin user seeded successfully");
    }
}
