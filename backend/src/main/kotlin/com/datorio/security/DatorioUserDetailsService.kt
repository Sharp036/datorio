package com.datorio.security

import com.datorio.model.User
import com.datorio.repository.UserRepository
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.security.core.userdetails.UserDetailsService
import org.springframework.security.core.userdetails.UsernameNotFoundException
import org.springframework.stereotype.Service

@Service
class DatorioUserDetailsService(
    private val userRepository: UserRepository
) : UserDetailsService {

    override fun loadUserByUsername(username: String): UserDetails {
        val user = findUser(username)
            ?: throw UsernameNotFoundException("User not found: $username")

        val authorities = mutableListOf<SimpleGrantedAuthority>()

        // Add role-based authorities
        user.roles.forEach { role ->
            authorities.add(SimpleGrantedAuthority("ROLE_${role.name}"))
            // Add permission-based authorities
            role.permissions.forEach { perm ->
                authorities.add(SimpleGrantedAuthority(perm.code))
            }
        }

        return org.springframework.security.core.userdetails.User.builder()
            .username(user.username)
            .password(user.passwordHash)
            .authorities(authorities)
            .disabled(!user.isActive)
            .build()
    }

    // The exact name first; otherwise a name typed in another case or with spaces around it, if only one user has it
    private fun findUser(username: String): User? =
        userRepository.findByUsername(username).orElse(null)
            ?: userRepository.findAllByUsernameIgnoreCase(username.trim()).singleOrNull()
}
