package net.bestia.zone.account

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Repository
import java.util.stream.Stream

@Repository
interface AccountRepository : JpaRepository<Account, Long> {
  @Query("""
    SELECT DISTINCT a
    FROM Account a
    JOIN FETCH a.master m
    JOIN FETCH m.bestias b
    """
  )
  fun streamAllWithMasterAndBestias(): Stream<Account>
}

fun AccountRepository.findByIdOrThrow(id: Long): Account {
  return findByIdOrNull(id) ?: throw AccountNotFoundException()
}

