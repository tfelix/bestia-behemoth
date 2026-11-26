package net.bestia.api.bugreport

import org.springframework.data.jpa.repository.JpaRepository

interface BugReportFileRepository : JpaRepository<BugReportFile, Long> {

  fun findAllByReportId(reportId: Long): List<BugReportFile>
}
