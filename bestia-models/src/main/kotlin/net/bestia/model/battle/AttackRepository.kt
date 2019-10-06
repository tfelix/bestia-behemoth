package net.bestia.model.battle

import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional

import org.springframework.data.repository.CrudRepository

@Repository
@Transactional(readOnly = true)
interface AttackRepository : CrudRepository<Attack, Long>
