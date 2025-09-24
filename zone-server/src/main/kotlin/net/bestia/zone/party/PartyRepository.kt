package net.bestia.zone.party

import net.bestia.zone.account.master.Master
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.repository.findByIdOrNull

interface PartyRepository : JpaRepository<Party, Long> {

  fun findByOwner(owner: Master): Party?


  fun findByMember(master: Master): Party?
}

fun PartyRepository.findByIdOrThrow(id: Long): Party {
  return findByIdOrNull(id) ?: throw PartyNotFoundException(id)
}