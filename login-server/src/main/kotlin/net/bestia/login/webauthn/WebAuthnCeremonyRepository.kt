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

  /** Answers 1 only for the caller that removed the row, so racing requests take a ceremony once. */
  @Modifying
  @Query("DELETE FROM WebAuthnCeremony c WHERE c.id = :id")
  fun deleteTaken(@Param("id") id: String): Int

  /** Ceremonies that would add a passkey to an existing account. */
  @Modifying
  @Query("DELETE FROM WebAuthnCeremony c WHERE c.accountId = :accountId")
  fun deleteAllForAccount(@Param("accountId") accountId: Long): Int
}
