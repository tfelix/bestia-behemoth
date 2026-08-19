package net.bestia.zone.ecs.account

import net.bestia.zone.account.master.BodyType
import net.bestia.zone.account.master.Face
import net.bestia.zone.account.master.Hairstyle
import net.bestia.zone.ecs.core.Component
import net.bestia.zone.util.EntityId
import net.bestia.zone.ecs.core.Dirtyable
import net.bestia.zone.ecs.core.World
import net.bestia.zone.ecs.SyncTargets
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

  private var dirty = true

  override fun isDirty(): Boolean {
    return dirty
  }

  override fun markDirty() {
    dirty = true
  }

  override fun clearDirty() {
    dirty = false
  }

  override fun toEntityMessage(entityId: Long, removed: Boolean): EntitySMSG {
    return MasterVisualComponentSMSG(entityId, name, skinColor, hairColor, face, body, hair)
  }

  override fun syncTargets(world: World, entityId: EntityId): SyncTargets = SyncTargets.PublicInRange
}