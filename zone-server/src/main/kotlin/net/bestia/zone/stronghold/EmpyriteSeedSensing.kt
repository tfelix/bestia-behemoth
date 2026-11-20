package net.bestia.zone.stronghold

import net.bestia.worldgen.stronghold.EmpyriteSeed
import net.bestia.zone.casting.SeedSensing
import net.bestia.zone.casting.sense.SenseKind
import net.bestia.zone.casting.sense.SenseMark
import net.bestia.zone.casting.sense.SenseMarksSMSG
import net.bestia.zone.ecs.core.World
import net.bestia.zone.identity.ecs.Account
import net.bestia.zone.identity.ecs.Master
import net.bestia.zone.message.OutMessageProcessor
import net.bestia.zone.movement.ecs.Position
import net.bestia.zone.util.EntityId
import net.bestia.zone.world.WorldService
import org.springframework.stereotype.Component
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.random.Random

/**
 * What Official Guild Approval shows its caster: the nearest awake seeds in range, each off its true spot by up to
 * a share of its distance, so a seed is only found by casting again closer. A seed the caster discovered carries
 * its name.
 */
@Component
class EmpyriteSeedSensing(
  private val seeds: EmpyriteSeedRegistry,
  private val discoveries: EmpyriteSeedDiscoveries,
  private val worldService: WorldService,
  private val messages: OutMessageProcessor,
  private val config: StrongholdConfig,
  private val random: Random = Random.Default,
) : SeedSensing {

  override fun sense(world: World, casterId: EntityId) {
    val accountId = world.get(casterId, Account::class)?.accountId ?: return
    val masterId = world.get(casterId, Master::class)?.masterId ?: return
    val at = world.get(casterId, Position::class)?.toVec3L() ?: return
    val voxel = worldService.config.voxelSize
    val east = at.x * voxel
    val north = at.y * voxel

    val marks = seeds.awake()
      .map { it to hypot(it.x - east, it.y - north) }
      .filter { (_, distance) -> distance <= config.senseRangeMetres }
      .sortedBy { (_, distance) -> distance }
      .take(config.senseMarks)
      .map { (seed, distance) -> markOf(seed, distance, masterId) }

    messages.sendToPlayer(accountId, SenseMarksSMSG(marks, VISIBLE_SECONDS))
  }

  private fun markOf(seed: EmpyriteSeed, distance: Double, masterId: Long): SenseMark {
    val error = distance * config.senseErrorShare * random.nextDouble()
    val angle = random.nextDouble(2 * PI)

    return SenseMark(
      kind = SenseKind.EMPYRITE_SEED,
      eastMetres = seed.x + cos(angle) * error,
      northMetres = seed.y + sin(angle) * error,
      label = if (discoveries.isDiscovered(masterId, seed.rank)) seed.name else "",
      refId = seed.rank.toLong(),
    )
  }

  companion object {
    const val VISIBLE_SECONDS = 60
  }
}
