package net.bestia.api.bugreport

import org.springframework.data.jpa.repository.JpaRepository

interface BugReportBanRepository : JpaRepository<BugReportBan, Long>
