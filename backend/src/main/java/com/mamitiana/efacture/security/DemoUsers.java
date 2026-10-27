package com.mamitiana.efacture.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Creates one account per role at startup, so the demo can be tried right away. Disable in production. */
@Component
@ConditionalOnProperty(name = "app.demo-users.enabled", havingValue = "true")
public class DemoUsers implements ApplicationRunner {

    public static final String MANAGER_EMAIL = "gerant@demo.fr";
    public static final String ACCOUNTANT_EMAIL = "comptable@demo.fr";

    private static final Logger log = LoggerFactory.getLogger(DemoUsers.class);

    private final AppUserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final String password;

    public DemoUsers(AppUserRepository users, PasswordEncoder passwordEncoder,
                     @Value("${app.demo-users.password}") String password) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.password = password;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        create(MANAGER_EMAIL, "Gérant (démo)", Role.MANAGER);
        create(ACCOUNTANT_EMAIL, "Comptable (démo)", Role.ACCOUNTANT);
    }

    private void create(String email, String displayName, Role role) {
        if (!users.existsByEmailIgnoreCase(email)) {
            users.save(new AppUser(email, displayName, passwordEncoder.encode(password), role));
            log.info("Demo account created: {} ({})", email, role);
        }
    }
}
