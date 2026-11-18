package net.bestia.zone.chronicle.persistence

import net.bestia.worldgen.core.ActorType
import org.springframework.data.jpa.repository.JpaRepository

interface WorldFactRepository : JpaRepository<WorldFact, Long> {

  /** Oldest first, so the last row is how things stand now. */
  fun findAllBySubjectTypeOrderById(subjectType: ActorType): List<WorldFact>

  fun findAllBySubjectTypeAndSubjectIndexOrderById(subjectType: ActorType, subjectIndex: Int): List<WorldFact>
}
