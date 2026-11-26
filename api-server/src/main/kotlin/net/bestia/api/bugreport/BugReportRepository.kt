package net.bestia.api.bugreport

import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import java.time.LocalDateTime

interface BugReportRepository : JpaRepository<BugReport, Long> {

  fun countByAccountIdAndCreatedAtAfter(accountId: Long, after: LocalDateTime): Long

  /** Reports in [status] whose next attempt is due, oldest first. */
  @Query(
    "select r from BugReport r where r.status = :status and (r.nextAttemptAt is null or r.nextAttemptAt <= :now) order by r.id"
  )
  fun findDue(status: BugReport.Status, now: LocalDateTime, page: Pageable): List<BugReport>
}
