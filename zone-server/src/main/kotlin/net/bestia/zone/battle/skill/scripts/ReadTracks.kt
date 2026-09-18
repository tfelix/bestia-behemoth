package net.bestia.zone.battle.skill.scripts

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.zone.battle.GroundBattleContext
import net.bestia.zone.battle.LineOfSightService
import net.bestia.zone.battle.damage.Damage
import net.bestia.zone.battle.skill.BasicMagicSkillStrategy
import net.bestia.zone.battle.skill.SkillContext
import net.bestia.zone.dialog.DialogArg
import net.bestia.zone.dialog.DialogId
import net.bestia.zone.dialog.DialogService
import net.bestia.zone.world.spoor.ActorKind
import net.bestia.zone.world.spoor.SpoorConfig
import net.bestia.zone.world.spoor.TrackReading
import org.springframework.stereotype.Component

/**
 * Reads what has walked over a patch of ground.
 *
 * ### What the skill level buys is *how much of the answer*, not whether there is one
 *
 * The ground either holds tracks or it does not, and that is decided by what the ground is made of rather than
 * by the tracker - see `SurfaceTrampleableGround.CAP_IMPRESSION`. What rank adds is reach, then a direction,
 * then what made them, then a name. So a novice standing on a fresh trail still learns something, and an
 * expert standing on cobblestone still learns nothing.
 *
 * ### No text crosses the wire
 *
 * Every part of the reading goes out as a `DialogArg`: a species is a translation key the client already has a
 * row for, a direction is one of eight keys, an age is one of three, and a count is a number. A player name is
 * the one thing sent as text, because it is a name.
 *
 * ### Finding nothing is not an error
 *
 * Reading empty ground is a cast that worked and came back with "nothing has been through here", which is
 * information and often the information that matters. So there is no `OpError` here - a refusal is for
 * something a player may not do, and this is not one.
 */
@Component
class ReadTracks(
  private val config: SpoorConfig,
  private val dialogService: DialogService,
  losService: LineOfSightService,
) : BasicMagicSkillStrategy(losService) {

  /**
   * The catalogued range is enforced here rather than trusted from the client, which is the whole reason this
   * extends the shared gate. `skills.yml` sets no `needsLineOfSight`: ground you can walk to is ground you can
   * crouch over, and refusing a reading because a bush is in the way would be a rule about bushes.
   */
  override fun isCastPossible(ctx: SkillContext): Boolean {
    return ctx.battle is GroundBattleContext && super.isCastPossible(ctx)
  }

  override fun execute(ctx: SkillContext): Damage? {
    ctx.requireGroundContext()

    val accountId = ctx.world.accountIdOf(ctx.casterId)
    if (accountId == null) {
      LOG.debug { "Entity ${ctx.casterId} has no account to report tracks to" }
      return null
    }

    // The aimed-at point, falling back to where the tracker stands - `skills.yml` catalogues this as GROUND
    // with a range, so the player picks the patch and throwing that choice away would be silently ignoring it.
    val centre = ctx.targetPosition ?: ctx.world.positionOf(ctx.casterId)
    if (centre == null) {
      LOG.debug { "Track reading by ${ctx.casterId} has no position to centre on" }
      return null
    }

    // Floored at rank 1: `KnownSkills.levelOf` answers 0 for a skill nobody has taken, and a zero radius would
    // search nothing at all.
    val level = ctx.skillLevel.coerceAtLeast(1)

    val reading = ctx.world.readTracks(centre, config.readRadiusPerLevelTiles * level)

    when {
      reading == null -> dialogService.send(accountId, DialogId.TRACKS_NONE)
      identityOf(reading, level) == null -> dialogService.send(accountId, DialogId.TRACKS_FAINT, faint(reading, level))
      else -> dialogService.send(accountId, DialogId.TRACKS_READING, named(reading, level))
    }

    return null
  }

  private fun faint(reading: TrackReading, level: Int) = mapOf(
    "heading" to headingOf(reading, level),
    "age" to ageOf(reading),
    "passages" to DialogArg.Number(reading.passages.toLong()),
    "walkers" to DialogArg.Number(reading.walkers.toLong()),
  )

  private fun named(reading: TrackReading, level: Int) = faint(reading, level) + mapOf(
    "what" to identityOf(reading, level)!!,
    "level" to DialogArg.Number((reading.signature?.level ?: 0).toLong()),
  )

  /**
   * What made the tracks, or null when this reading cannot say - which is either a rank too low to tell or a
   * trail older than the world's memory of what walked it.
   */
  private fun identityOf(reading: TrackReading, level: Int): DialogArg? {
    if (level < config.identityLevel) return null

    val signature = reading.signature ?: return null

    if (signature.kind == ActorKind.MASTER) {
      // A master's name is already public through MasterVisualComponentSMSG, so the gate is about the skill
      // having ranks worth taking rather than about hiding anything.
      return if (level >= config.nameLevel && !signature.masterName.isNullOrBlank()) {
        DialogArg.Name(signature.masterName)
      } else {
        DialogArg.Token(TRAVELLER)
      }
    }

    return signature.nameToken?.let { DialogArg.Token(it) }
  }

  private fun headingOf(reading: TrackReading, level: Int): DialogArg {
    if (level < config.headingLevel) return DialogArg.Token(DIRECTION_UNKNOWN)

    return DialogArg.Token(DIRECTIONS[reading.octant.coerceIn(DIRECTIONS.indices)])
  }

  private fun ageOf(reading: TrackReading): DialogArg = DialogArg.Token(
    when {
      reading.ageSeconds <= config.freshSeconds -> AGE_FRESH
      reading.ageSeconds >= config.coldSeconds -> AGE_COLD
      else -> AGE_RECENT
    }
  )

  private companion object {
    val LOG = KotlinLogging.logger { }

    /**
     * The eight headings, in the order `MarkingGroundTrample.octantOf` numbers them: 0 towards +x, counting
     * towards +y.
     *
     * +x is east and +y is north, which is the world generator's own convention - `OceanBorder` measures the
     * north edge as `worldHeight - y`. Naming them anywhere else would be a second opinion about which way
     * the world faces.
     */
    val DIRECTIONS = arrayOf(
      "DIRECTION_EAST",
      "DIRECTION_NORTHEAST",
      "DIRECTION_NORTH",
      "DIRECTION_NORTHWEST",
      "DIRECTION_WEST",
      "DIRECTION_SOUTHWEST",
      "DIRECTION_SOUTH",
      "DIRECTION_SOUTHEAST",
    )

    const val DIRECTION_UNKNOWN = "DIRECTION_UNKNOWN"
    const val TRAVELLER = "TRACK_SUBJECT_TRAVELLER"
    const val AGE_FRESH = "TRACK_AGE_FRESH"
    const val AGE_RECENT = "TRACK_AGE_RECENT"
    const val AGE_COLD = "TRACK_AGE_COLD"
  }
}
