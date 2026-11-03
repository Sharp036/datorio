package com.datorio

import com.datorio.model.User
import com.datorio.repository.UserRepository
import com.datorio.security.DatorioUserDetailsService
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.*
import org.springframework.security.core.userdetails.UsernameNotFoundException
import java.util.*

class UserDetailsLookupTest {

    private val users = mutableListOf<User>()
    private val repo = mock<UserRepository> {
        on { findByUsername(any()) } doAnswer { inv ->
            Optional.ofNullable(users.firstOrNull { it.username == inv.getArgument<String>(0) })
        }
        on { findAllByUsernameIgnoreCase(any()) } doAnswer { inv ->
            users.filter { it.username.equals(inv.getArgument<String>(0), ignoreCase = true) }
        }
    }
    private val service = DatorioUserDetailsService(repo)

    private fun user(name: String) = User(username = name, email = "$name@example.com", passwordHash = "hash")
        .also { users.add(it) }

    @Test
    fun `finds the exact name`() {
        user("j.smith")
        assertEquals("j.smith", service.loadUserByUsername("j.smith").username)
    }

    @Test
    fun `finds a name typed in another case or with spaces and returns the stored name`() {
        user("j.smith")
        assertEquals("j.smith", service.loadUserByUsername("J.smith").username)
        assertEquals("j.smith", service.loadUserByUsername(" J.SMITH ").username)
    }

    @Test
    fun `prefers the exact name when names differ only in case`() {
        user("j.smith")
        user("J.Smith")
        assertEquals("J.Smith", service.loadUserByUsername("J.Smith").username)
    }

    @Test
    fun `does not guess between names that differ only in case`() {
        user("j.smith")
        user("J.Smith")
        assertThrows<UsernameNotFoundException> { service.loadUserByUsername("J.SMITH") }
    }

    @Test
    fun `an unknown name is not found`() {
        user("j.smith")
        assertThrows<UsernameNotFoundException> { service.loadUserByUsername("smith") }
    }
}
