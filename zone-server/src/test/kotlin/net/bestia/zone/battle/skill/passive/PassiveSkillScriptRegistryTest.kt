package net.bestia.zone.battle.skill.passive

import net.bestia.zone.battle.damage.Damage
import net.bestia.zone.battle.skill.SkillContext
import net.bestia.zone.battle.skill.SkillStrategyFactory
import net.bestia.zone.battle.skill.SkillStrategy
import net.bestia.zone.battle.skill.SkillTargetType
import net.bestia.zone.battle.status.StatusValueRecalcContext
import net.bestia.zone.skill.Skill
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class PassiveSkillScriptRegistryTest {

  private class InnerPeaceStub : PassiveSkillScript {
    override val skillIdentifier = "INNER_PEACE"
    override fun apply(context: StatusValueRecalcContext, level: Int) = context.addHpRegen(percent = 3 * level)
  }

  private class MissingSkillScript : PassiveSkillScript {
    override val skillIdentifier = "NO_SUCH_SKILL"
    override fun apply(context: StatusValueRecalcContext, level: Int) = Unit
  }

  private class CastableSkillScript : PassiveSkillScript {
    override val skillIdentifier = "FIREBOLT"
    override fun apply(context: StatusValueRecalcContext, level: Int) = Unit
  }

  /** Named `Firebolt`, so the strategy factory keys it under that name and FIREBOLT counts as castable. */
  private class Firebolt : SkillStrategy {
    override fun isCastPossible(ctx: SkillContext) = true
    override fun execute(ctx: SkillContext): Damage? = null
  }

  private val strategies = SkillStrategyFactory(listOf(Firebolt()))

  private fun skill(id: Long, identifier: String, script: String? = null) = Skill(
    id = id,
    identifier = identifier,
    strength = null,
    script = script,
    manaCost = 0,
    range = null,
    targetType = SkillTargetType.FRIENDLY,
    needsLineOfSight = false,
    requiredLevel = 0
  )

  private val innerPeace = skill(27L, "INNER_PEACE")

  /**
   * A passive nobody has written a script for. Most of the catalogue looks like this: with `Skill.type` gone,
   * "no castable script" is exactly what makes a skill passive, so binding must stay indifferent to it.
   */
  private val divineProtection = skill(2L, "DIVINE_PROTECTION")

  private val firebolt = skill(5L, "FIREBOLT", script = "Firebolt")

  private fun registry(vararg scripts: PassiveSkillScript) =
    PassiveSkillScriptRegistry(scripts.toList(), strategies)

  @Test
  fun `a script binds to the skill it names`() {
    val script = InnerPeaceStub()
    val registry = registry(script)

    registry.bind(listOf(innerPeace, divineProtection, firebolt))

    assertEquals(1, registry.bound().size)
    assertSame(script, registry.bound()[innerPeace.id])
    assertSame(script, registry.get("InnerPeaceStub"))
    assertNull(registry.get("NotARegisteredScript"))
  }

  @Test
  fun `a passive skill with no script bean is not an error`() {
    // Deliberately only validated in one direction. Roughly twenty catalogued passives have no
    // implementation, and failing boot on them would make the server unbootable against any real
    // database - the same reason SkillScriptBootValidator warns rather than throws.
    val registry = registry(InnerPeaceStub())

    registry.bind(listOf(innerPeace, divineProtection))

    assertEquals(1, registry.bound().size)
    assertNull(registry.bound()[divineProtection.id])
  }

  @Test
  fun `a script naming an unknown skill fails binding`() {
    // The typo case. A script bean exists in code, so a miss can only be a mistake - unlike the
    // reverse direction, it has no legitimate in-between state and must not go quietly inert.
    val registry = registry(MissingSkillScript())

    val ex = assertThrows<PassiveSkillScriptBindingException> { registry.bind(listOf(innerPeace)) }

    assertEquals(true, ex.message!!.contains("NO_SUCH_SKILL"))
  }

  @Test
  fun `a script naming a castable skill fails binding`() {
    // A skill is either cast or always-on, never both: the two would read the same invested level and
    // nothing decides which one a point bought. This is the check that replaced `type != PASSIVE`.
    val registry = registry(CastableSkillScript())

    val ex = assertThrows<PassiveSkillScriptBindingException> { registry.bind(listOf(firebolt)) }

    assertEquals(true, ex.message!!.contains("FIREBOLT"))
  }

  @Test
  fun `binding is empty before it has run`() {
    // The registry is a bean long before PassiveSkillScriptBinderBootRunner fires; anything reading
    // it in that window must see nothing rather than a half-built map.
    assertEquals(emptyMap<Long, PassiveSkillScript>(), registry(InnerPeaceStub()).bound())
  }
}
