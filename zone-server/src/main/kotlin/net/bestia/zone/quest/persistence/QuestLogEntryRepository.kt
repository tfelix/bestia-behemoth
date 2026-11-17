package net.bestia.zone.quest.persistence

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query

interface QuestLogEntryRepository : JpaRepository<QuestLogEntry, Long> {

  @Query("select e from QuestLogEntry e join fetch e.quest where e.masterId = :masterId")
  fun findAllByMasterId(masterId: Long): List<QuestLogEntry>

  fun findByMasterIdAndQuestId(masterId: Long, questId: Long): QuestLogEntry?

  fun findAllByQuestId(questId: Long): List<QuestLogEntry>

  fun countByMasterId(masterId: Long): Long

  fun deleteAllByQuestIdIn(questIds: Collection<Long>)
}
