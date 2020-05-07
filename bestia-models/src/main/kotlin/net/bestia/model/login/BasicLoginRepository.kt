package net.bestia.model.login

import org.springframework.data.repository.CrudRepository
import org.springframework.stereotype.Repository

@Repository
interface BasicLoginRepository : CrudRepository<BasicLogin, Long> {

  fun findByEmail(email: String): BasicLogin?
}