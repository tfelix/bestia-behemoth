package net.bestia.model.dao

import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.CrudRepository
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

import net.bestia.model.domain.Account

/**
 * AccountDAO for accessing the database in order to get [Account] objects using Hibernate.
 *
 * @author Thomas Felix
 */
@Repository("accountDao")
interface AccountDAO : CrudRepository<Account, Long> {

  /**
   * Searches an account for a provided email address. Since e-mails are unique only one [Account] is returned or
   * `null`.
   *
   * @param email
   * Email adress to look for.
   * @return Account if found or `null`.
   */
  fun findByEmail(email: String): Account?

  /**
   * Checks the nickname of the designated master. If this a master with this nickname is found then the apropriate
   * [Account] is returned.
   *
   * @param username
   * @return Account with the master with this nickname or `null`.
   */
  @Query("FROM Account a WHERE a.username = :username")
  fun findByUsername(@Param("username") username: String): Account?

  /**
   * Returns the account via its username or if its mail if the username did
   * not match (username takes preference about email). If none could be found
   * null is returned.
   */
  @Query("FROM Account a WHERE a.username = :username OR a.email = :username")
  fun findByUsernameOrEmail(@Param("username") username: String): Account?
}