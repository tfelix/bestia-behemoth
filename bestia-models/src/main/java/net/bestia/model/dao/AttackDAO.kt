package net.bestia.model.dao

import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional

import net.bestia.model.domain.AttackImpl
import org.springframework.data.repository.CrudRepository

@Repository("attackDao")
@Transactional(readOnly = true)
interface AttackDAO : CrudRepository<AttackImpl, Int>
