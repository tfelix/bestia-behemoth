package net.bestia.zone.chat

import net.bestia.zone.ecs.core.World
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.identity.ecs.Account
import net.bestia.zone.identity.ecs.Master
import net.bestia.zone.message.OutMessageProcessor
import net.bestia.zone.movement.ecs.Position
import net.bestia.zone.worldboss.WorldTremors
import org.springframework.stereotype.Component
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.roundToInt

/** Tells every master online which way the ground trembled, as each of them feels it. */
@Component
class TremorLines(
  private val out: OutMessageProcessor,
) : WorldTremors {

  override fun feel(world: World, at: Vec3L) {
    world.query(Master::class, Account::class, Position::class).each { _ ->
      val here = get<Position>().toVec3L()
      out.sendToPlayer(get<Account>().accountId, ChatSMSG(type = ChatType.BROADCAST, text = lineFor(here, at)))
    }
  }

  private fun lineFor(here: Vec3L, at: Vec3L): String {
    val dx = (at.x - here.x).toDouble()
    val dy = (at.y - here.y).toDouble()
    if (hypot(dx, dy) <= NEAR_METRES) {
      return "The ground heaves beneath you. Something old is awake."
    }

    return "Far to the ${compass(atan2(dy, dx))}, the ground trembles. Something old is awake."
  }

  /** Eight points from a bearing in radians, x east and y north. */
  private fun compass(bearing: Double): String {
    val sector = (((bearing / (2.0 * PI)) * 8.0).roundToInt() % 8 + 8) % 8
    return COMPASS[sector]
  }

  private companion object {
    const val NEAR_METRES = 2_000.0

    /** Counted anticlockwise from east, as `atan2` is. */
    val COMPASS = listOf("east", "north-east", "north", "north-west", "west", "south-west", "south", "south-east")
  }
}
