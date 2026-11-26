package net.bestia.api.bugreport

import org.springframework.data.jpa.repository.JpaRepository
import java.time.LocalDateTime

interface BugReportRepository : JpaRepository<BugReport, Long> {

  fun countByAccountIdAndCreatedAtAfter(accountId: Long, after: LocalDateTime): Long
}
