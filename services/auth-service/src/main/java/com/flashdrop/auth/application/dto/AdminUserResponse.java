package com.flashdrop.auth.application.dto;

import java.util.List;

/** Vista administrativa de un usuario: identidad minima y roles, sin datos sensibles. */
public record AdminUserResponse(Long id, String name, String lastName, String email, List<String> roles) {
}
