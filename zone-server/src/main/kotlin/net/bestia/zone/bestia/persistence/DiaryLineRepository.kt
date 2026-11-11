package net.bestia.zone.bestia.persistence

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.Instant

interface DiaryLineRepository : JpaRepository<DiaryLine, Long> {

  fun findAllByMasterIdOrderByAtDesc(masterId: Long): List<DiaryLine>

  fun countByPlayerBestiaId(playerBestiaId: Long): Long

  fun findAllByPlayerBestiaIdOrderByAtDesc(playerBestiaId: Long): List<DiaryLine>

  @Modifying
  @Query("delete from DiaryLine l where l.at < :before")
  fun deleteOlderThan(@Param("before") before: Instant): Int

  fun deleteAllByMasterId(masterId: Long)
}
