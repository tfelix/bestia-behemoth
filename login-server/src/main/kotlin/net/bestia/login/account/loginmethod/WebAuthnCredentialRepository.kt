package net.bestia.login.account.loginmethod

import org.springframework.data.jpa.repository.JpaRepository

interface WebAuthnCredentialRepository : JpaRepository<WebAuthnCredential, Long> {
  fun findByCredentialId(credentialId: ByteArray): WebAuthnCredential?

  fun findAllByAccountId(accountId: Long): List<WebAuthnCredential>

  fun existsByCredentialId(credentialId: ByteArray): Boolean

  fun countByAccountId(accountId: Long): Long
}
