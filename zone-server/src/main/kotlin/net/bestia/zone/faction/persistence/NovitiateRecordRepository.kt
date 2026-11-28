package net.bestia.zone.faction.persistence

import org.springframework.data.jpa.repository.JpaRepository

interface NovitiateRecordRepository : JpaRepository<NovitiateRecord, Long> {

  fun findByQuestId(questId: Long): NovitiateRecord?
}
