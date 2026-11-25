package net.bestia.zone.inn

import net.bestia.zone.townsfolk.conversation.TalkService
import net.bestia.zone.util.AccountId
import net.bestia.zone.util.EntityId
import org.springframework.stereotype.Component

/** The inn counter a request names, re-checked on every request because a client can name any entity. */
@Component
class InnCounters(
  private val talk: TalkService,
  private val keepers: InnKeepers,
) {

  fun inReach(accountId: AccountId, actor: EntityId, target: EntityId): InnCounter? {
    return talk.speakerInReach(accountId, actor, target)?.let(keepers::counterOf)
  }
}
