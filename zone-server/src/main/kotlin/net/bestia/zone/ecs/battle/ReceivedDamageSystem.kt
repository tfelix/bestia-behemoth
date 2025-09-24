package net.bestia.zone.ecs.battle

import com.github.quillraven.fleks.Entity
import com.github.quillraven.fleks.IteratingSystem
import com.github.quillraven.fleks.World
import com.github.quillraven.fleks.World.Companion.inject
import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.zone.item.ItemEntityFactory
import net.bestia.zone.message.entity.VanishEntitySMSG
import net.bestia.zone.message.processor.OutMessageProcessor
import net.bestia.zone.ecs.EntityRegistry
import net.bestia.zone.ecs.movement.Position
import net.bestia.zone.ecs.visual.BestiaVisual
import kotlin.math.floor

class ReceivedDamageSystem : IteratingSystem(
  World.family { all(Damage) }
) {

  override fun onTickEntity(entity: Entity) {
    val receivedDamage = entity[Damage]

    entity.configure {
      it -= Damage
    }

    if (entity.hasNo(TakenDamage)) {
      entity.configure {
        it += TakenDamage()
      }
    }

    // Entity vs entity id... urg
    // val takenDamage = entity[TakenDamage]
    // takenDamage.addDamage()

    if (entity.has(Health)) {
      val health = entity[Health]
      health.current -= receivedDamage.total()
      if (health.current == 0) {
        entity.configure {
          it += Dead
        }
      }
    }
  }

  companion object {
    private val LOG = KotlinLogging.logger { }
  }
}