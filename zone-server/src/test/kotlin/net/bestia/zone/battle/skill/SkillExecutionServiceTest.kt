package net.bestia.zone.battle.skill

import io.mockk.every
import io.mockk.mockk
import net.bestia.zone.battle.BattleContextFactory
import net.bestia.zone.battle.damage.Damage
import net.bestia.zone.battle.damage.HitDamage
import net.bestia.zone.ecs.battle.status.Mana
import net.bestia.zone.ecs.battle.status.StatusValues
import net.bestia.zone.ecs.core.AsyncJobExecutor
import net.bestia.zone.ecs.core.World
import net.bestia.zone.ecs.core.testWorld
import net.bestia.zone.ecs.movement.Position
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.skill.Skill
import net.bestia.zone.skill.SkillRepository
import net.bestia.zone.util.EntityId
import net.bestia.zone.world.prop.PropPromotionService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.Optional
import net.bestia.zone.ecs.battle.damage.Damage as DamageComponent

class SkillExecutionServiceTest {

  /** Records what it was asked to do and reports whether it ran, since resolution has no return value. */
  private class TestScript(
    private val result: Damage? = null,
    private val possible: Boolean = true,
    private val body: (SkillContext) -> Unit = {},
  ) : SkillStrategy {
    var casts = 0
      private set

    override fun isCastPossible(ctx: SkillContext) = possible

    override fun execute(ctx: SkillContext): Damage? {
      casts++
      body(ctx)

      return result
    }
  }

  private val world = testWorld()

  /** Runs submitted work inline, so a test asserts on the outcome instead of racing a worker pool. */
  private val asyncJobs: AsyncJobExecutor = mockk<AsyncJobExecutor>().also {
    every { it.submit(any(), any()) } answers { secondArg<() -> Unit>()() }
    every { it.submit(any<() -> Unit>()) } answers { firstArg<() -> Unit>()() }
  }

  private val propPromotion = PropPromotionService(mockk(relaxed = true))

  @Test
  fun `a resolved cast lands its number on the target`() {
    val script = TestScript(result = HitDamage(42))
    val caster = world.spawnFighter()
    val target = world.spawnFighter()

    serviceFor(script).execute(world, caster, SKILL_ID, 1, target, null)

    assertEquals(1, script.casts)
    assertEquals(42, world.stagedDamageOn(target))
  }

  @Test
  fun `a script that refuses the cast spends nothing`() {
    val script = TestScript(result = HitDamage(42), possible = false)
    val caster = world.spawnFighter(mana = 100)
    val target = world.spawnFighter()

    serviceFor(script, manaCost = 30).execute(world, caster, SKILL_ID, 1, target, null)

    assertEquals(0, script.casts)
    assertEquals(100, world.manaOf(caster), "a refused cast must not be charged for")
    assertEquals(0, world.stagedDamageOn(target))
  }

  @Test
  fun `a caster who cannot pay never reaches the script`() {
    val script = TestScript(result = HitDamage(42))
    val caster = world.spawnFighter(mana = 5)
    val target = world.spawnFighter()

    serviceFor(script, manaCost = 30).execute(world, caster, SKILL_ID, 1, target, null)

    assertEquals(0, script.casts)
    assertEquals(5, world.manaOf(caster))
  }

  @Test
  fun `a resolved cast is charged exactly once`() {
    val script = TestScript(result = HitDamage(1))
    val caster = world.spawnFighter(mana = 100)
    val target = world.spawnFighter()

    serviceFor(script, manaCost = 30).execute(world, caster, SKILL_ID, 1, target, null)

    assertEquals(70, world.manaOf(caster))
  }

  @Test
  fun `two casts on the same target add up rather than replacing each other`() {
    // The reason the old implementation needed `world.defer`: off the tick thread each modify scope holds the
    // lock outright, so the get-or-create on the Damage component is atomic and both hits survive.
    val script = TestScript(result = HitDamage(10))
    val caster = world.spawnFighter()
    val target = world.spawnFighter()
    val service = serviceFor(script)

    service.execute(world, caster, SKILL_ID, 1, target, null)
    service.execute(world, caster, SKILL_ID, 1, target, null)

    assertEquals(20, world.stagedDamageOn(target))
  }

  @Test
  fun `a script that overruns its budget fizzles the cast rather than the server`() {
    val runaway = TestScript(body = { ctx -> repeat(1_000) { ctx.world.positionOf(ctx.casterId) } })
    val caster = world.spawnFighter()
    val target = world.spawnFighter()

    // Throwing out of here would kill the AsyncJobExecutor worker in production, so the test is that it does
    // not throw at all.
    serviceFor(runaway).execute(world, caster, SKILL_ID, 1, target, null)

    assertEquals(1, runaway.casts)
    assertEquals(0, world.stagedDamageOn(target), "the number is never applied once the script was cut off")
  }

  @Test
  fun `a skill with no script implementation is ignored rather than throwing`() {
    val caster = world.spawnFighter()
    val target = world.spawnFighter()
    val service = service(skill(script = "NotImplemented"), strategies = SkillStrategyFactory(emptyList()))

    service.execute(world, caster, SKILL_ID, 1, target, null)

    assertEquals(0, world.stagedDamageOn(target))
  }

  @Test
  fun `a skill id that is not in the catalogue is ignored rather than throwing`() {
    val caster = world.spawnFighter()
    val repository = mockk<SkillRepository>()
    every { repository.findById(any()) } returns Optional.empty()

    val service = SkillExecutionService(
      repository,
      SkillStrategyFactory(emptyList()),
      contextFactory(),
      asyncJobs
    )

    service.execute(world, caster, 9999L, 1, null, Vec3L(1, 0, 0))

    assertFalse(world.has(caster, DamageComponent::class))
  }

  @Test
  fun `a ground cast resolves with no target to put a number on`() {
    val script = TestScript(result = HitDamage(7))
    val caster = world.spawnFighter()

    serviceFor(script).execute(world, caster, SKILL_ID, 1, null, Vec3L(2, 0, 0))

    assertEquals(1, script.casts, "a ground cast still runs; it just has nothing to show the number on")
  }

  @Test
  fun `the script sees the level the cast was activated at`() {
    var seen = 0
    val script = TestScript(body = { seen = it.skillLevel })
    val caster = world.spawnFighter()
    val target = world.spawnFighter()

    serviceFor(script).execute(world, caster, SKILL_ID, 7, target, null)

    assertEquals(7, seen)
  }

  @Test
  fun `the script is given a world it can actually read`() {
    val at = Vec3L(4, 0, 0)
    var seen: Vec3L? = null
    val script = TestScript(body = { seen = it.world.positionOf(it.casterId) })
    val caster = world.spawnFighter(at = at)
    val target = world.spawnFighter()

    serviceFor(script).execute(world, caster, SKILL_ID, 1, target, null)

    assertEquals(at, seen)
    assertTrue(script.casts == 1)
  }

  private fun serviceFor(script: SkillStrategy, manaCost: Int = 0) =
    service(skill(manaCost = manaCost), SkillStrategyFactory(listOf(script)))

  private fun service(skill: Skill, strategies: SkillStrategyFactory): SkillExecutionService {
    val repository = mockk<SkillRepository>()
    every { repository.findById(skill.id) } returns Optional.of(skill)

    return SkillExecutionService(repository, strategies, contextFactory(), asyncJobs)
  }

  private fun contextFactory() = SkillContextFactory(
    BattleContextFactory(propPromotion),
    SkillWorldServices(
      aoi = mockk(relaxed = true),
      structures = mockk(relaxed = true),
      areaEffectSpawner = mockk(relaxed = true),
      groundFire = mockk(relaxed = true),
      statusEffects = mockk(relaxed = true),
      messages = mockk(relaxed = true),
      crafting = mockk(relaxed = true),
      survey = mockk(relaxed = true),
    ),
    // A tight op budget so the runaway case is reached in a few hundred calls rather than the production 64.
    SkillExecutionConfig(worldOpsPerCast = 16, maxQueryResults = 8, maxMillisPerCast = 60_000)
  )

  /** [script] defaults to `TestScript`, the simple name a `SkillStrategyFactory` keys the test script under. */
  private fun skill(manaCost: Int = 0, script: String = "TestScript") = Skill(
    id = SKILL_ID,
    identifier = "TEST_SKILL",
    strength = null,
    script = script,
    manaCost = manaCost,
    range = 100,
    targetType = SkillTargetType.ENEMY,
    needsLineOfSight = false,
    requiredLevel = 0
  )

  private fun World.spawnFighter(mana: Int = 0, at: Vec3L = Vec3L(1, 0, 0)): EntityId = createEntity { id ->
    add(id, Position.fromVec3(at))
    add(id, StatusValues(strength = 10, intelligence = 10, vitality = 10, dexterity = 10, willpower = 10, agility = 10))
    if (mana > 0) add(id, Mana(mana, mana))
  }

  private fun World.manaOf(id: EntityId): Int = get(id, Mana::class)?.current ?: 0

  private fun World.stagedDamageOn(id: EntityId): Int = get(id, DamageComponent::class)?.total() ?: 0

  private companion object {
    const val SKILL_ID = 1L
  }
}
