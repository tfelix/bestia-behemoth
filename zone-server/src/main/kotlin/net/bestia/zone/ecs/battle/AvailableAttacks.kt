package net.bestia.zone.ecs.battle

import com.github.quillraven.fleks.Component
import com.github.quillraven.fleks.ComponentType
import com.github.quillraven.fleks.Entity
import com.github.quillraven.fleks.World
import net.bestia.zone.ecs.ComponentNotFoundException
import net.bestia.zone.ecs.WorldAcessor

class AvailableAttacks(
  val availableAttacks: Map<Long, Int>
) : Component<AvailableAttacks> {

  class AvailableAttacksAccessor(
    private val entity: Entity
  ) : WorldAcessor {

    var availableAttacks: Map<Long, Int> = emptyMap()
      private set

    override fun doWithWorld(world: World) {
      val comp = with(world) {
        entity.getOrNull(AvailableAttacks)
          ?: throw ComponentNotFoundException(AvailableAttacks)
      }

      availableAttacks = comp.availableAttacks
    }

    fun knowsAttack(attackId: Long, attemptedSkillLevel: Int): Boolean {
      // Basic attack is always known.
      if(attackId == 0L) {
        return true
      }

      return availableAttacks[attackId]
        ?.let { availableSkillLv -> availableSkillLv >= attemptedSkillLevel }
        ?: false
    }
  }

  override fun type() = AvailableAttacks

  companion object : ComponentType<AvailableAttacks>()
}