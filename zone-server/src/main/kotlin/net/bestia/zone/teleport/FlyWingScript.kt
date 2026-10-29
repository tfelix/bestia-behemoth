package net.bestia.zone.teleport

import net.bestia.zone.battle.StatusEffectService
import net.bestia.zone.ecs.core.World
import net.bestia.zone.entity.ecs.EntityFx
import net.bestia.zone.message.OutMessageProcessor
import net.bestia.zone.movement.ecs.Teleporting
import net.bestia.zone.util.EntityId
import org.springframework.stereotype.Component

/** `items.yml` id 74, Fly Wing: a hop to a random spot nearby, on ground the player already sees. */
@Component
class FlyWingScript(
  statusEffects: StatusEffectService,
  out: OutMessageProcessor,
  private val settings: TeleportItemSettings,
) : WingScript(statusEffects, out) {

  override val itemId: Long = 74L

  override val vanish = EntityFx.Kind.WING_VANISH
  override val vanishMs: Int get() = settings.flyWingVanishMs

  override fun destination(world: World, userId: EntityId): Teleporting.Destination =
    Teleporting.Destination.Nearby(settings.flyWingRadiusMetres)
}
