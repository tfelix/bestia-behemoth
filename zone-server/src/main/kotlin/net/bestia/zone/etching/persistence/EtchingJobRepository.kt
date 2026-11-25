package net.bestia.zone.etching.persistence

import org.springframework.data.jpa.repository.JpaRepository
import java.time.Instant

interface EtchingJobRepository : JpaRepository<EtchingJob, Long> {

  fun findAllByStructureId(structureId: Long): List<EtchingJob>

  fun findAllByEndsAtLessThanEqual(now: Instant): List<EtchingJob>
}
