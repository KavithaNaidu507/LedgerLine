package com.ledgerline.security;

import com.ledgerline.domain.User;

/** Who is calling the API. Built from the database on every request (never trusted from the token alone). */
public record UserPrincipal(Long id, String email, User.Role role) {}
