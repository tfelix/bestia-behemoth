package net.bestia.login.webauthn

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.LocalDateTime

interface WebAuthnCeremonyRepository : JpaRepository<WebAuthnCeremony, String> {

  @Modifying
  @Query("DELETE FROM WebAuthnCeremony c WHERE c.expiresAt < :cutoff")
  fun deleteExpired(@Param("cutoff") cutoff: LocalDateTime): Int

  /** Ceremonies that would add a passkey to an existing account. */
  @Modifying
  @Query("DELETE FROM WebAuthnCeremony c WHERE c.accountId = :accountId")
  fun deleteAllForAccount(@Param("accountId") accountId: Long): Int
}
