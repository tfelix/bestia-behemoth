package net.bestia.model.account

import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.CrudRepository
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.util.stream.Stream

@Repository
interface AccountRepository : CrudRepository<Account, Long> {

  /**
   * Checks the nickname of the designated master. If this a master with this nickname is found then the apropriate
   * [Account] is returned.
   *
   * @param username
   * @return Account with the master with this nickname or `null`.
   */
  fun findByUsername(@Param("username") username: String): Account?

  @Query("SELECT a FROM Account a")
  fun findAllStream(): Stream<Account>
}