package net.bestia.zone.battle.attack

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.repository.findByIdOrNull

interface AttackRepository : JpaRepository<Attack, Long>

fun AttackRepository.findByIdOrThrow(id: Long): Attack {
  return findByIdOrNull(id) ?: throw AttackNotFoundException(id)
}
