package com.ledgerline.service;

import com.ledgerline.domain.User;
import com.ledgerline.repo.UserRepository;
import com.ledgerline.security.JwtService;
import com.ledgerline.web.ApiException;
import com.ledgerline.web.Dtos.*;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {
    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final JwtService jwt;
    private final AuditService audit;

    public AuthService(UserRepository users, PasswordEncoder encoder, JwtService jwt, AuditService audit) {
        this.users = users; this.encoder = encoder; this.jwt = jwt; this.audit = audit;
    }

    /** Public registration always creates an ACCOUNT_HOLDER. Only an admin can change roles. */
    @Transactional
    public AuthResponse register(RegisterRequest r) {
        String email = r.email().trim().toLowerCase();
        if (users.existsByEmail(email)) {
            throw new ApiException(HttpStatus.CONFLICT, "EMAIL_TAKEN", "An account with this email already exists");
        }
        User u = users.save(new User(email, encoder.encode(r.password()), r.fullName().trim(), User.Role.ACCOUNT_HOLDER));
        audit.log(u.getId(), "USER_REGISTERED", "USER", String.valueOf(u.getId()), null);
        return new AuthResponse(jwt.create(u), Dto.of(u));
    }

    @Transactional
    public AuthResponse login(LoginRequest r) {
        User u = users.findByEmail(r.email().trim().toLowerCase()).orElse(null);
        if (u == null || !encoder.matches(r.password(), u.getPasswordHash())) {
            audit.log(u == null ? null : u.getId(), "LOGIN_FAILED", "USER", u == null ? null : String.valueOf(u.getId()), null);
            // same message for "no such user" and "wrong password" so attackers learn nothing
            throw new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "Invalid email or password");
        }
        audit.log(u.getId(), "LOGIN", "USER", String.valueOf(u.getId()), null);
        return new AuthResponse(jwt.create(u), Dto.of(u));
    }

    @Transactional(readOnly = true)
    public UserDto me(Long id) {
        return Dto.of(users.findById(id).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "User not found")));
    }

    @Transactional(readOnly = true)
    public List<UserDto> listUsers() {
        return users.findAllByOrderById().stream().map(Dto::of).toList();
    }

    @Transactional
    public UserDto changeRole(Long adminId, Long userId, String role) {
        User.Role newRole;
        try { newRole = User.Role.valueOf(role); }
        catch (IllegalArgumentException e) { throw new ApiException(HttpStatus.BAD_REQUEST, "BAD_ROLE", "Role must be ADMIN, ACCOUNT_HOLDER or AUDITOR"); }
        if (adminId.equals(userId)) throw new ApiException(HttpStatus.BAD_REQUEST, "SELF_ROLE_CHANGE", "You cannot change your own role");
        User u = users.findById(userId).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "User not found"));
        String old = u.getRole().name();
        u.setRole(newRole);
        audit.log(adminId, "ROLE_CHANGED", "USER", String.valueOf(userId), old + " -> " + newRole);
        return Dto.of(u);
    }

    /** tiny alias so the lines above stay short */
    private static final class Dto {
        static UserDto of(User u) { return com.ledgerline.web.Dtos.of(u); }
    }
}
