package net.bestia.zone.text.persistence

import org.springframework.data.jpa.repository.JpaRepository

interface WorldTextRepository : JpaRepository<WorldText, WorldText.Key> {

  fun findAllBySubject(subject: String): List<WorldText>

  fun findAllByIdLocaleAndState(locale: String, state: WorldText.State): List<WorldText>

  fun findFirstByIdLocaleAndStateOrderByPriorityAscSubjectAsc(locale: String, state: WorldText.State): WorldText?

  fun countByIdLocaleAndState(locale: String, state: WorldText.State): Long
}
