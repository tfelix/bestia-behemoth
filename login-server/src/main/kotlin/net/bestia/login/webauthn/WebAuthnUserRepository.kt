package net.bestia.login.webauthn

import org.springframework.data.jpa.repository.JpaRepository

interface WebAuthnUserRepository : JpaRepository<WebAuthnUser, Long> {
  fun findByUserHandle(userHandle: ByteArray): WebAuthnUser?
}
