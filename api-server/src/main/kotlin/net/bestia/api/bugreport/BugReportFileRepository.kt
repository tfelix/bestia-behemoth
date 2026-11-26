package net.bestia.api.bugreport

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.transaction.annotation.Transactional

interface BugReportFileRepository : JpaRepository<BugReportFile, Long> {

  fun findAllByReportId(reportId: Long): List<BugReportFile>

  @Transactional
  @Modifying
  @Query("delete from BugReportFile f where f.reportId = :reportId")
  fun deleteAllOfReport(reportId: Long)
}
