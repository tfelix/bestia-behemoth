package net.bestia.login.gamelogin

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.LocalDateTime

interface LoginSessionRepository : JpaRepository<LoginSession, String> {

  @Modifying
  @Query("DELETE FROM LoginSession s WHERE s.expiresAt < :cutoff")
  fun deleteExpired(@Param("cutoff") cutoff: LocalDateTime): Int

  /** Conditional, so two browsers opening the same link at once cannot both claim it. */
  @Modifying
  @Query(
    "UPDATE LoginSession s SET s.browserBindingHash = :bindingHash " +
      "WHERE s.idHash = :idHash AND s.browserBindingHash IS NULL " +
      "AND s.status = net.bestia.login.gamelogin.LoginSessionStatus.PENDING AND s.expiresAt > :now"
  )
  fun claimBrowser(
    @Param("idHash") idHash: String,
    @Param("bindingHash") bindingHash: String,
    @Param("now") now: LocalDateTime
  ): Int
}
