package net.bestia.zone.battle.skill.scripts

import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import net.bestia.zone.battle.BattleContextFixture
import net.bestia.zone.battle.LineOfSightService
import net.bestia.zone.battle.skill.RecordingSkillWorld
import net.bestia.zone.battle.skill.SkillContextFixture
import net.bestia.zone.dialog.DialogArg
import net.bestia.zone.dialog.DialogId
import net.bestia.zone.dialog.DialogService
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.world.spoor.ActorKind
import net.bestia.zone.world.spoor.ActorSignature
import net.bestia.zone.world.spoor.SpoorConfig
import net.bestia.zone.world.spoor.TrackReading
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * What a tracker is told, and what their rank has not earned them yet.
 *
 * The level gates are the behaviour: the ground either holds tracks or it does not, and rank only decides how
 * much of the answer comes back. Everything asserted here is an argument rather than a sentence, because no
 * text crosses the wire.
 */
class ReadTracksTest {

  private val config = SpoorConfig()

  private val dialogs = mockk<DialogService>(relaxed = true)
  private val sut = ReadTracks(config, dialogs, LineOfSightService())

  private val casterAccount = 77L

  private fun blob() = ActorSignature(
    kind = ActorKind.BESTIA,
    speciesId = 3,
    level = 12,
    identifier = "blob",
    masterName = null,
  )

  private fun master(name: String = "Rhea") = ActorSignature(
    kind = ActorKind.MASTER,
    speciesId = 5,
    level = 40,
    identifier = "",
    masterName = name,
  )

  private fun reading(
    signature: ActorSignature? = blob(),
    passages: Int = 6,
    ageSeconds: Long = 10,
    octant: Int = 2,
    walkers: Int = 1,
  ) = TrackReading(signature, passages, ageSeconds, octant, walkers)

  /** Runs the skill and returns the dialog it sent, with its arguments. */
  private fun cast(
    level: Int,
    found: TrackReading? = reading(),
    targetPosition: Vec3L = Vec3L(3, 0, 0),
  ): Pair<DialogId, Map<String, DialogArg>> {
    val dialog = slot<DialogId>()
    val args = slot<Map<String, DialogArg>>()
    every { dialogs.send(any(), capture(dialog), capture(args), any()) } returns Unit

    val world = RecordingSkillWorld().apply { tracks = found }
    val battle = BattleContextFixture.groundCtx(targetPosition = targetPosition)
    world.putMaster(battle.attacker.id, masterId = 1, accountId = casterAccount)

    sut.execute(SkillContextFixture.skillCtx(battle, world, skillLevel = level))

    return dialog.captured to args.captured
  }

  @Test
  fun `clean ground is a reading, not a failure`() {
    val (dialog, args) = cast(level = 5, found = null)

    assertEquals(DialogId.TRACKS_NONE, dialog)
    assertTrue(args.isEmpty())
  }

  @Test
  fun `a novice is told there are tracks but not what left them`() {
    val (dialog, args) = cast(level = 1)

    assertEquals(DialogId.TRACKS_FAINT, dialog)
    assertEquals(DialogArg.Number(6), args["passages"])
    assertNull(args["what"], "identity was reported below the rank that earns it")
  }

  @Test
  fun `a novice is not told which way the trail led`() {
    assertEquals(DialogArg.Token("DIRECTION_UNKNOWN"), cast(level = 1).second["heading"])
  }

  @Test
  fun `the heading arrives once the rank earns it`() {
    // Octant 2 is +y, which the world generator calls north.
    assertEquals(DialogArg.Token("DIRECTION_NORTH"), cast(level = config.headingLevel).second["heading"])
  }

  @Test
  fun `a creature is named by a key the client translates itself`() {
    val (dialog, args) = cast(level = config.identityLevel)

    assertEquals(DialogId.TRACKS_READING, dialog)
    assertEquals(DialogArg.Token("BESTIA_BLOB"), args["what"])
    assertEquals(DialogArg.Number(12), args["level"])
  }

  /** The user's own question: a blob against something level 80. The level is what answers it. */
  @Test
  fun `how strong it was comes with what it was`() {
    val boss = blob().copy(speciesId = 9, level = 80, identifier = "doom_master_of_doom")

    val args = cast(level = config.identityLevel, found = reading(signature = boss)).second

    assertEquals(DialogArg.Token("BESTIA_DOOM_MASTER_OF_DOOM"), args["what"])
    assertEquals(DialogArg.Number(80), args["level"])
  }

  @Test
  fun `another master reads as a traveller until the rank that names them`() {
    assertEquals(
      DialogArg.Token("TRACK_SUBJECT_TRAVELLER"),
      cast(level = config.identityLevel, found = reading(signature = master())).second["what"]
    )

    assertEquals(
      DialogArg.Name("Rhea"),
      cast(level = config.nameLevel, found = reading(signature = master())).second["what"]
    )
  }

  /**
   * A trail older than the world's memory of what walked it. Distinct from a rank too low, and the player is
   * told the same thing either way - which is correct, since neither one of them can say.
   */
  @Test
  fun `a trail nobody can place reads as faint even at full rank`() {
    val (dialog, args) = cast(level = 9, found = reading(signature = null))

    assertEquals(DialogId.TRACKS_FAINT, dialog)
    assertNull(args["what"])
  }

  @Test
  fun `a fresh trail and a cold one are told apart`() {
    assertEquals(
      DialogArg.Token("TRACK_AGE_FRESH"),
      cast(level = 1, found = reading(ageSeconds = 0)).second["age"]
    )
    assertEquals(
      DialogArg.Token("TRACK_AGE_RECENT"),
      cast(level = 1, found = reading(ageSeconds = config.freshSeconds + 1)).second["age"]
    )
    assertEquals(
      DialogArg.Token("TRACK_AGE_COLD"),
      cast(level = 1, found = reading(ageSeconds = config.coldSeconds)).second["age"]
    )
  }

  @Test
  fun `the search is centred on the aimed-at point and widens with rank`() {
    val world = RecordingSkillWorld().apply { tracks = null }
    val aimedAt = Vec3L(3, 0, 0)
    val battle = BattleContextFixture.groundCtx(targetPosition = aimedAt)
    world.putMaster(battle.attacker.id, masterId = 1, accountId = casterAccount)

    sut.execute(SkillContextFixture.skillCtx(battle, world, skillLevel = 3))

    val read = world.trackReads.single()

    assertEquals(aimedAt, read.centre)
    assertEquals(config.readRadiusPerLevelTiles * 3, read.radiusTiles)
  }

  /** `KnownSkills.levelOf` answers 0 for a skill nobody has taken, and a zero radius would search nothing. */
  @Test
  fun `an untaken rank still searches something`() {
    val world = RecordingSkillWorld().apply { tracks = null }
    val battle = BattleContextFixture.groundCtx()
    world.putMaster(battle.attacker.id, masterId = 1, accountId = casterAccount)

    sut.execute(SkillContextFixture.skillCtx(battle, world, skillLevel = 0))

    assertTrue(world.trackReads.single().radiusTiles > 0)
  }

  @Test
  fun `an entity target is refused - this reads ground`() {
    assertTrue(!sut.isCastPossible(SkillContextFixture.skillCtx(BattleContextFixture.entityCtx())))
  }

  /** The catalogued reach is the server's, not the client's. */
  @Test
  fun `ground beyond the skill's range cannot be read`() {
    assertTrue(sut.isCastPossible(SkillContextFixture.skillCtx(BattleContextFixture.groundCtx())))
    assertTrue(
      !sut.isCastPossible(
        SkillContextFixture.skillCtx(BattleContextFixture.groundCtx(targetPosition = Vec3L(500, 0, 0)))
      )
    )
  }
}
