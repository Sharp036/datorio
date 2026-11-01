package com.datorio.config

import com.datorio.model.User
import com.datorio.repository.RoleRepository
import com.datorio.repository.UserRepository
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class AdminInitializer(
    private val userRepository: UserRepository,
    private val roleRepository: RoleRepository,
    private val passwordEncoder: PasswordEncoder,
    @Value("\${datorio.admin.username}") private val adminUsername: String,
    @Value("\${datorio.admin.password}") private val adminPassword: String,
    @Value("\${datorio.admin.email}") private val adminEmail: String,
    @Value("\${datorio.jwt.secret}") private val jwtSecret: String
) : ApplicationRunner {

    private val log = LoggerFactory.getLogger(javaClass)

    @Transactional
    override fun run(args: ApplicationArguments?) {
        warnAboutExampleSecrets()
        if (userRepository.existsByUsername(adminUsername)) {
            log.info("Admin user '$adminUsername' already exists, skipping initialization")
            return
        }

        val adminRole = roleRepository.findByName("ADMIN")
            .orElseThrow { IllegalStateException("ADMIN role not found — check Flyway migration") }

        val admin = User(
            username = adminUsername,
            email = adminEmail,
            passwordHash = passwordEncoder.encode(adminPassword),
            displayName = "System Administrator"
        )
        admin.roles.add(adminRole)
        userRepository.save(admin)

        log.info("Default admin user '$adminUsername' created successfully")
    }

    // The quick start works with the values of .env.example; an installation open to a network must not
    private fun warnAboutExampleSecrets() {
        val placeholder = jwtSecret.length < 32 || jwtSecret.uppercase().let { it.contains("CHANGE_ME") || it.contains("CHANGE-ME") || it.contains("YOUR_") }
        if (placeholder) {
            log.warn("JWT_SECRET is the example value or shorter than 32 characters: anyone who reads .env.example can forge an admin token. Set a random JWT_SECRET before exposing this installation")
        }
        if (adminPassword == "admin") {
            log.warn("ADMIN_PASSWORD is 'admin': change it in .env or in the profile before exposing this installation")
        }
    }
}
