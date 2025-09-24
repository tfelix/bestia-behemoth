package net.bestia.zone.ecs.player

import com.github.quillraven.fleks.Component
import com.github.quillraven.fleks.ComponentType
import com.github.quillraven.fleks.Entity
import com.github.quillraven.fleks.World
import net.bestia.zone.ecs.ComponentNotFoundException
import net.bestia.zone.ecs.WorldAcessor

data class Account(
  var accountId: Long,
) : Component<Account> {

  class AccountOwnerAcessor(
    private val entity: Entity
  ) : WorldAcessor {

    var accountId: Long = 0
      private set

    override fun doWithWorld(world: World) {
      val comp = with(world) {
        entity.getOrNull(Account)
          ?: throw ComponentNotFoundException(Account)
      }

      accountId = comp.accountId
    }
  }

  override fun type(): ComponentType<Account> = Account

  companion object : ComponentType<Account>()
}