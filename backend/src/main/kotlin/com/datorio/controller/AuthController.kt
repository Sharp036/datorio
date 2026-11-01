package com.datorio.controller

import com.datorio.model.dto.*
import com.datorio.service.AuthService
import jakarta.validation.Valid
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.web.bind.annotation.*

@RestController
class HealthController {

    @GetMapping("/health")
    fun health(): Map<String, Any> = mapOf(
        "status" to "UP",
        "service" to "Datorio",
        "version" to "1.0.0",
        "timestamp" to java.time.OffsetDateTime.now().toString()
    )
}

@RestController
@RequestMapping("/auth")
class AuthController(
    private val authService: AuthService,
    // REGISTRATION_ENABLED=true lets anyone create a VIEWER account; otherwise administrators create users
    @Value("\${datorio.auth.registration-enabled:false}") private val registrationEnabled: Boolean
) {
    @PostMapping("/login")
    fun login(@Valid @RequestBody request: LoginRequest): ResponseEntity<TokenResponse> {
        return ResponseEntity.ok(authService.login(request))
    }

    @PostMapping("/register")
    fun register(@Valid @RequestBody request: RegisterRequest): ResponseEntity<Any> {
        if (!registrationEnabled) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(mapOf("message" to "Self-registration is disabled; ask an administrator for an account"))
        }
        return ResponseEntity.ok(authService.register(request))
    }

    @PostMapping("/refresh")
    fun refresh(@Valid @RequestBody request: RefreshTokenRequest): ResponseEntity<TokenResponse> {
        return ResponseEntity.ok(authService.refreshToken(request))
    }

    @GetMapping("/me")
    fun me(@AuthenticationPrincipal userDetails: UserDetails?): ResponseEntity<UserResponse> {
        if (userDetails == null) return ResponseEntity.status(401).build()
        return ResponseEntity.ok(authService.getCurrentUser(userDetails.username))
    }
}
