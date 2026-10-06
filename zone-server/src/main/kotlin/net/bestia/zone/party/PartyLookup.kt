package net.bestia.zone.party

import org.springframework.data.repository.findByIdOrNull
import net.bestia.zone.account.persistence.Party
import net.bestia.zone.account.persistence.PartyRepository

fun PartyRepository.findByIdOrThrow(id: Long): Party {
  return findByIdOrNull(id) ?: throw PartyNotFoundException(id)
}

fun PartyRepository.findByIdForUpdateOrThrow(id: Long): Party {
  return findByIdForUpdate(id) ?: throw PartyNotFoundException(id)
}
