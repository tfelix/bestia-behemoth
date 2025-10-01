package net.bestia.zone.account.master

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Repository

@Repository
interface MasterRepository : JpaRepository<Master, Long> {
  fun findByName(name: String): Master?
}

fun MasterRepository.findByIdOrThrow(id: Long): Master {
  return findByIdOrNull(id) ?: throw MasterNotFoundException()
}