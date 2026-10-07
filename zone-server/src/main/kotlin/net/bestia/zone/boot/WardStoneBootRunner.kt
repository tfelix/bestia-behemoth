package net.bestia.zone.boot

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.zone.battle.StatusEffectService
import net.bestia.zone.battle.status.StatusEffectId
import net.bestia.zone.ecs.core.World
import net.bestia.zone.ecs.core.modify
import net.bestia.zone.entity.StaticEntityKind
import net.bestia.zone.entity.ecs.EntityVisual
import net.bestia.zone.entity.ecs.VisualKind
import net.bestia.zone.script.ecs.ScriptComponent
import net.bestia.zone.script.persistence.ScriptEntityPersister
import net.bestia.zone.util.EntityId
import org.springframework.boot.CommandLineRunner
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component

/**
 * Raises the ward stone at each home village: the script entity [ScriptEntityPersister] keeps at every master
 * spawn point gets its look and the `WARD_AURA` spell. On every boot, after [EntityLoaderBootRunner] brought
 * the entities back, because neither part is stored.
 */
@Component
@Order(112)
class WardStoneBootRunner(
  private val world: World,
  private val statusEffectService: StatusEffectService,
) : CommandLineRunner {

  override fun run(vararg args: String?) {
    val stones = wardStones()

    for (stone in stones) {
      world.modify(stone) { id ->
        add(id, EntityVisual(VisualKind.STRUCTURE, StaticEntityKind.WARD_STONE.ordinal.toLong()))
        statusEffectService.applyEffect(this, id, StatusEffectId.WARD_AURA, level = 1)
      }
    }

    LOG.info { "Raised ${stones.size} ward stone(s)" }
  }

  private fun wardStones(): List<EntityId> {
    val stones = mutableListOf<EntityId>()
    world.each(ScriptComponent::class) { id, script ->
      if (script.scriptId == ScriptEntityPersister.SPAWN_POINT_SCRIPT_ID) {
        stones.add(id)
      }
    }

    return stones
  }

  private companion object {
    val LOG = KotlinLogging.logger { }
  }
}
