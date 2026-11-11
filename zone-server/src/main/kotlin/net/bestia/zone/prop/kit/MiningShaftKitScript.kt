package net.bestia.zone.prop.kit

import net.bestia.zone.ecs.core.World
import net.bestia.zone.entity.StaticEntityKind
import net.bestia.zone.item.mining.MiningSkill
import net.bestia.zone.prop.PlayerStructureService
import net.bestia.zone.script.ScriptArgs
import net.bestia.zone.skill.ecs.KnownSkills
import net.bestia.zone.util.EntityId
import org.springframework.stereotype.Component

/** The Mining Shaft Kit (`items.yml` id 443), made with Carpentry. Only a miner can put one up. */
@Component
class MiningShaftKitScript(
  structures: PlayerStructureService,
  private val mining: MiningSkill,
) : StructureKitScript(structures, StaticEntityKind.MINING_SHAFT, BUILD_SECONDS) {

  override val itemId = 443L

  override fun execute(world: World, userId: EntityId, args: ScriptArgs): Boolean {
    if (mining.levelOf(world.get(userId, KnownSkills::class)) < 1) return false

    return super.execute(world, userId, args)
  }

  private companion object {
    /** A head frame and a ladder down: the biggest thing a kit puts up. */
    const val BUILD_SECONDS = 60f
  }
}
