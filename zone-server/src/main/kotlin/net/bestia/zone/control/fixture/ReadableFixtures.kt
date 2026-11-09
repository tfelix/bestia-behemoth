package net.bestia.zone.control.fixture

import net.bestia.zone.prop.PropInteraction

import net.bestia.bnet.proto.OperationErrorProto.OpError
import net.bestia.worldgen.core.GenRng
import net.bestia.worldgen.core.FigureRole
import net.bestia.worldgen.dress.CemeteryChannels
import net.bestia.worldgen.dress.FixtureChannels
import net.bestia.worldgen.dress.FixtureKind
import net.bestia.worldgen.history.Names
import net.bestia.worldgen.render.optionalAttribute
import net.bestia.worldgen.vector.Aabb
import net.bestia.worldgen.vector.AreaFeature
import net.bestia.worldgen.vector.FeatureKind
import net.bestia.worldgen.vector.PointMarker
import net.bestia.zone.chat.ChatSMSG
import net.bestia.zone.chat.ChatType
import net.bestia.zone.ecs.core.World
import net.bestia.zone.entity.StaticEntityKind
import net.bestia.zone.entity.ecs.PropPose
import net.bestia.zone.entity.ecs.WorldObjectIdentity
import net.bestia.zone.identity.ecs.Account
import net.bestia.zone.message.OutMessageProcessor
import net.bestia.zone.movement.ecs.Position
import net.bestia.zone.townsfolk.knowledge.ChronicleNames
import net.bestia.zone.util.EntityId
import net.bestia.zone.world.WorldService
import org.springframework.stereotype.Component
import java.util.Locale
import kotlin.math.PI
import kotlin.math.roundToInt

/**
 * What a signpost, a milestone or a headstone says, read off the world tier.
 *
 * The text is not stored anywhere: the dressing stage wrote settlement indices, distances and bearings onto the
 * fixture's marker, and the chronicle has every name. So reading one is finding the marker under the prop and
 * rendering it, and the answer is a chat line to the reader alone. A plain headstone has no marker - the chunk
 * tier fills a cemetery's plots itself - so its name is drawn from the prop's own identity, from the same name
 * pools the chronicle uses, with years inside the life of the settlement that buried it.
 */
@Component
class ReadableFixtures(
  private val worldService: WorldService,
  private val outMessageProcessor: OutMessageProcessor
) {

  fun read(world: World, actorId: EntityId, propId: EntityId, kind: FixtureKind): OpError? {
    val pose = world.get(propId, PropPose::class) ?: return null
    val actorAt = world.get(actorId, Position::class)?.toVec3L()
    // The client walks into reach before it sends the click, so a read from further off is a stale click rather
    // than a request worth an error of its own.
    if (actorAt != null && actorAt.distance(pose.position) > READ_RANGE) return null
    val accountId = world.get(actorId, Account::class)?.accountId ?: return null

    val text = textOf(world, propId, pose, kind) ?: return null
    outMessageProcessor.sendToPlayer(accountId, ChatSMSG(type = ChatType.COMMAND, text = text))
    return null
  }

  /** The line a fixture reads as, or null when the world under it no longer carries what it named. */
  fun textOf(world: World, propId: EntityId, pose: PropPose, kind: FixtureKind): String? {
    if (!worldService.isLoaded) return null
    val generated = worldService.generated
    val voxel = generated.config.voxelSize
    val x = (pose.position.x + 0.5) * voxel
    val y = (pose.position.y + 0.5) * voxel
    val chronicle = generated.world.chronicle

    if (kind == FixtureKind.GRAVESTONE) {
      val identity = world.get(propId, WorldObjectIdentity::class)?.propId ?: pose.position.hashCode().toLong()
      return plainGrave(generated.world.features.query(Aabb(x - 1, y - 1, x + 1, y + 1)), x, y, identity)
    }

    val marker = generated.world.features.query(Aabb(x - MARKER_SNAP, y - MARKER_SNAP, x + MARKER_SNAP, y + MARKER_SNAP))
      .filter { it.kind == FeatureKind.FIXTURE }
      .filterIsInstance<PointMarker>()
      .filter { it.optionalAttribute(FixtureChannels.KIND)?.toInt() == kind.ordinal }
      .minByOrNull { (it.position.x - x) * (it.position.x - x) + (it.position.y - y) * (it.position.y - y) }
      ?: return null

    return when (kind) {
      FixtureKind.SIGNPOST -> "The signpost reads: " + destinations(marker, x, y, withBearing = true)
      FixtureKind.MILESTONE -> "The milestone reads: " + destinations(marker, x, y, withBearing = false)
      FixtureKind.NAMED_GRAVE -> {
        val figure = chronicle.figures.getOrNull(marker.attribute(FixtureChannels.FIGURE).toInt()) ?: return null
        val name = ChronicleNames.figureOf(chronicle, figure.index) ?: return null
        val role = figure.role.name.lowercase(Locale.ROOT)
        "Here lies $name, $role, ${yearOf(figure.birthYear)}-${yearOf(figure.deathYear)}."
      }
      else -> null
    }
  }

  private fun destinations(marker: PointMarker, x: Double, y: Double, withBearing: Boolean): String {
    val chronicle = worldService.generated.world.chronicle
    val parts = ArrayList<String>()
    for (arm in 0 until FixtureChannels.MAX_DESTINATIONS) {
      val settlement = marker.optionalAttribute(FixtureChannels.destination(arm))?.toInt() ?: continue
      if (settlement < 0) continue
      val name = ChronicleNames.placeOf(chronicle, settlement) ?: continue
      val metres = marker.attribute(FixtureChannels.distance(arm))
      val bearing = marker.attribute(FixtureChannels.bearing(arm))
      parts += if (withBearing) "$name ${distanceText(metres)} ${compass(bearing)}" else "$name ${distanceText(metres)}"
    }
    return if (parts.isEmpty()) "the paint has worn away." else parts.joinToString(" · ") + "."
  }

  /** A plain headstone: a name from the identity, years inside the life of the settlement whose cemetery it is in. */
  private fun plainGrave(nearby: List<net.bestia.worldgen.vector.VectorFeature>, x: Double, y: Double, identity: Long): String {
    val chronicle = worldService.generated.world.chronicle
    val cemetery = nearby.filter { it.kind == FeatureKind.CEMETERY }.filterIsInstance<AreaFeature>()
      .firstOrNull { it.contains(x, y) }
    val settlement = cemetery?.perimeter?.let { table ->
      runCatching { table.valueAt(table.channel(CemeteryChannels.SETTLEMENT), 0).toInt() }.getOrNull()
    } ?: -1
    val record = chronicle.settlements.getOrNull(settlement)
    val culture = if (settlement >= 0) ChronicleNames.cultureOfSettlement(chronicle, settlement) else -1

    val seed = GenRng.hash(identity, GRAVE_NAME_SALT)
    val name = Names.person(seed, culture, FigureRole.entries[(GenRng.unit(seed) * FigureRole.entries.size).toInt()
      .coerceAtMost(FigureRole.entries.size - 1)])
    if (GenRng.unit(GenRng.mix64(seed + 1)) < WORN_SHARE) return "The headstone is too worn to read."

    val founded = record?.foundedYear?.takeIf { it != 0 } ?: (chronicle.presentYear - 200)
    val span = (chronicle.presentYear - founded).coerceAtLeast(30)
    val death = founded + 20 + (GenRng.unit(GenRng.mix64(seed + 2)) * (span - 20)).toInt()
    val birth = death - 18 - (GenRng.unit(GenRng.mix64(seed + 3)) * 60).toInt()
    return "Here lies $name, ${yearOf(birth)}-${yearOf(death)}."
  }

  private fun distanceText(metres: Double): String = if (metres >= 1000.0) {
    val km = (metres / 100.0).roundToInt() / 10.0
    "${if (km % 1.0 == 0.0) km.toInt().toString() else String.format(Locale.ROOT, "%.1f", km)} km"
  } else {
    "${(metres / 10.0).roundToInt() * 10} m"
  }

  /** Eight-point compass from a bearing in radians, x east and y north. */
  private fun compass(bearing: Double): String {
    val sector = (((bearing / (2.0 * PI)) * 8.0).roundToInt() % 8 + 8) % 8
    return COMPASS[sector]
  }

  /** A chronicle year as a player reads it: the year itself, which runs negative before the present era. */
  private fun yearOf(year: Int): String = year.toString()

  private companion object {
    const val READ_RANGE = 8L
    const val MARKER_SNAP = 1.5
    const val WORN_SHARE = 0.2
    const val GRAVE_NAME_SALT = 0x4E414D45L

    /** Counted anticlockwise from east, as `atan2` is. */
    val COMPASS = listOf("east", "north-east", "north", "north-west", "west", "south-west", "south", "south-east")
  }
}

/** One click target per readable fixture kind, all reading through [ReadableFixtures]. */
abstract class ReadableFixtureInteraction(
  private val fixture: FixtureKind,
  private val fixtures: ReadableFixtures
) : PropInteraction {
  override val kind: StaticEntityKind = StaticEntityKind.valueOf("FIXTURE_${fixture.name}")

  override fun interact(world: World, actorId: EntityId, propId: EntityId): OpError? =
    fixtures.read(world, actorId, propId, fixture)
}

@Component
class SignpostInteraction(fixtures: ReadableFixtures) : ReadableFixtureInteraction(FixtureKind.SIGNPOST, fixtures)

@Component
class MilestoneInteraction(fixtures: ReadableFixtures) : ReadableFixtureInteraction(FixtureKind.MILESTONE, fixtures)

@Component
class GravestoneInteraction(fixtures: ReadableFixtures) : ReadableFixtureInteraction(FixtureKind.GRAVESTONE, fixtures)

@Component
class NamedGraveInteraction(fixtures: ReadableFixtures) : ReadableFixtureInteraction(FixtureKind.NAMED_GRAVE, fixtures)
