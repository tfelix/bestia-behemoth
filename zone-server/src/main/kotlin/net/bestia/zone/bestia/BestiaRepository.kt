package net.bestia.zone.bestia

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Repository

@Repository
interface BestiaRepository : JpaRepository<Bestia, Long> {

  fun findByIdentifier(identifier: String): Bestia?
}

fun BestiaRepository.findByIdentifierOrThrow(identifier: String): Bestia {
  return findByIdentifier(identifier)
    ?: throw BestiaNotFoundException(identifier)
}

fun BestiaRepository.findByIdOrThrow(id: Long): Bestia {
  return findByIdOrNull(id) ?: throw BestiaNotFoundException(id)
}