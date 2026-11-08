package com.ledgerline.web;

import com.ledgerline.security.UserPrincipal;
import com.ledgerline.service.AuthService;
import com.ledgerline.web.Dtos.*;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class AuthController {
    private final AuthService auth;

    public AuthController(AuthService auth) { this.auth = auth; }

    @PostMapping("/auth/register")
    @ResponseStatus(HttpStatus.CREATED)
    public AuthResponse register(@Valid @RequestBody RegisterRequest r) { return auth.register(r); }

    @PostMapping("/auth/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest r) { return auth.login(r); }

    @GetMapping("/me")
    public UserDto me(@AuthenticationPrincipal UserPrincipal me) { return auth.me(me.id()); }

    // ----- ADMIN only (also enforced in SecurityConfig for /api/admin/**) -----
    @GetMapping("/admin/users")
    public List<UserDto> users() { return auth.listUsers(); }

    @PatchMapping("/admin/users/{id}/role")
    public UserDto changeRole(@AuthenticationPrincipal UserPrincipal me, @PathVariable Long id, @Valid @RequestBody RoleRequest r) {
        return auth.changeRole(me.id(), id, r.role());
    }
}
