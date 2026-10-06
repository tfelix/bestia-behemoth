package net.bestia.zone.master.ecs

import net.bestia.zone.account.BodyType
import net.bestia.zone.account.Face
import net.bestia.zone.account.Hairstyle
import net.bestia.zone.ecs.core.Component
import net.bestia.zone.util.EntityId
import net.bestia.zone.ecs.core.DirtyFlag
import net.bestia.zone.sync.Dirtyable
import net.bestia.zone.ecs.core.World
import net.bestia.zone.sync.SyncTargets
import net.bestia.zone.message.EntitySMSG
import java.awt.Color

/**
 * What another player sees of a master: their body, and their name.
 *
 * The name rides here rather than on a component of its own because it is the same fact under the same rule -
 * public, fixed for as long as the entity lives, and needed by every viewer exactly when the body arrives.
 */
data class MasterVisual(
  val id: Int,
  val name: String,
  val skinColor: Color,
  val hairColor: Color,
  val face: Face,
  val body: BodyType,
  val hair: Hairstyle
) : Component, Dirtyable {
  override val dirtyFlag = DirtyFlag()

  override fun toEntityMessage(entityId: Long, removed: Boolean): EntitySMSG {
    return MasterVisualComponentSMSG(entityId, name, skinColor, hairColor, face, body, hair)
  }

  override fun syncTargets(world: World, entityId: EntityId): SyncTargets = SyncTargets.PublicInRange
}