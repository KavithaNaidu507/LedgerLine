package com.ledgerline.service;

import com.ledgerline.domain.User;
import com.ledgerline.repo.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/** On first start, creates one ADMIN and one AUDITOR so you can demo the role dashboards. */
@Component
public class DataSeeder implements ApplicationRunner {
    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final String adminEmail, auditorEmail, password;

    public DataSeeder(UserRepository users, PasswordEncoder encoder,
                      @Value("${ledgerline.seed.admin-email}") String adminEmail,
                      @Value("${ledgerline.seed.auditor-email}") String auditorEmail,
                      @Value("${ledgerline.seed.password}") String password) {
        this.users = users; this.encoder = encoder;
        this.adminEmail = adminEmail; this.auditorEmail = auditorEmail; this.password = password;
    }

    @Override
    public void run(ApplicationArguments args) {
        seed(adminEmail, "Platform Admin", User.Role.ADMIN);
        seed(auditorEmail, "Read-only Auditor", User.Role.AUDITOR);
    }

    private void seed(String email, String name, User.Role role) {
        if (!users.existsByEmail(email)) users.save(new User(email, encoder.encode(password), name, role));
    }
}
