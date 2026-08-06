package net.bestia.zone.account.master

import net.bestia.zone.account.AccountRepository
import net.bestia.zone.account.findByIdOrThrow
import net.bestia.zone.util.AccountId
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

/**
 * Creates a master for an account id, in a transaction of its own.
 *
 * The isolation is the point. Master names are kept unique by an index, so "that name is taken" is
 * discovered as a constraint violation — and a constraint violation marks its transaction rollback-only
 * the instant it happens. Reporting `NAME_ALREADY_TAKEN` is a perfectly ordinary answer to give a player
 * at the character-creation screen, but doing so from inside the transaction that just failed leaves the
 * caller holding one that can no longer commit: it replies to the client, returns normally, and then dies
 * at commit with `UnexpectedRollbackException`, far away from the code that caused it.
 *
 * `REQUIRES_NEW` rather than relying on callers not to be transactional, because that is a property no
 * caller can see it needs to have. This way the doomed transaction is always this one, and whatever the
 * caller is doing survives being told the name was taken.
 *
 * The propagation specifically, not merely the annotation: with `REQUIRED` this is worse than having no
 * boundary at all, because Spring marks the *shared* transaction rollback-only whenever an exception
 * escapes a transactional boundary. That extends the failure to every rejection this can produce,
 * including the pure validation ones — invalid name, master-slot limit, unknown spawn point — which never
 * touched the database. Verified by flipping it: `REQUIRED` fails four `MasterCreateScenario` cases where
 * the unguarded version failed only the duplicate-name one.
 *
 * A separate bean rather than a method on [MasterFactory] because propagation is applied by Spring's
 * proxy, and a self-invocation inside `MasterFactory` would bypass it and silently do nothing.
 */
@Component
class MasterCreateOperation(
  private val accountRepository: AccountRepository,
  private val masterFactory: MasterFactory
) {

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  fun create(
    accountId: AccountId,
    createMasterData: MasterFactory.CreateMasterData
  ): Master {
    val account = accountRepository.findByIdOrThrow(accountId)

    return masterFactory.create(account, createMasterData)
  }
}
