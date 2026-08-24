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
}
