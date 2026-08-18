package net.bestia.zone.battle.skill.passive

import net.bestia.zone.battle.skill.SkillTargetType
import net.bestia.zone.battle.skill.SkillType
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

  /** [script] is only non-null for castable skills, which `Skill` itself requires to have one. */
  private fun skill(id: Long, identifier: String, type: SkillType, script: String? = null) = Skill(
    id = id,
    identifier = identifier,
    strength = null,
    type = type,
    script = script,
    manaCost = 0,
    range = null,
    targetType = SkillTargetType.FRIENDLY,
    needsLineOfSight = false,
    requiredLevel = 0
  )

  private val innerPeace = skill(27L, "INNER_PEACE", SkillType.PASSIVE)

  /**
   * A PASSIVE skill nobody has written a script for. Most of the catalogue looks like this, and
   * `DIVINE_PROTECTION` additionally carries a `script:` name in `skills.yml` with no bean behind
   * it - a leftover from before passives had a pipeline. Binding must stay indifferent to both.
   */
  private val divineProtection = skill(2L, "DIVINE_PROTECTION", SkillType.PASSIVE)

  private val firebolt = skill(5L, "FIREBOLT", SkillType.NO_DAMAGE, script = "Firebolt")

  @Test
  fun `a script binds to the skill it names`() {
    val script = InnerPeaceStub()
    val registry = PassiveSkillScriptRegistry(listOf(script))

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
    val registry = PassiveSkillScriptRegistry(listOf(InnerPeaceStub()))

    registry.bind(listOf(innerPeace, divineProtection))

    assertEquals(1, registry.bound().size)
    assertNull(registry.bound()[divineProtection.id])
  }

  @Test
  fun `a script naming an unknown skill fails binding`() {
    // The typo case. A script bean exists in code, so a miss can only be a mistake - unlike the
    // reverse direction, it has no legitimate in-between state and must not go quietly inert.
    val registry = PassiveSkillScriptRegistry(listOf(MissingSkillScript()))

    val ex = assertThrows<PassiveSkillScriptBindingException> { registry.bind(listOf(innerPeace)) }

    assertEquals(true, ex.message!!.contains("NO_SUCH_SKILL"))
  }

  @Test
  fun `a script naming a non-passive skill fails binding`() {
    // Only passives are folded into the status recalc; a castable skill resolves through
    // SkillStrategy instead, and naming one here would silently never fire.
    val registry = PassiveSkillScriptRegistry(listOf(CastableSkillScript()))

    val ex = assertThrows<PassiveSkillScriptBindingException> { registry.bind(listOf(firebolt)) }

    assertEquals(true, ex.message!!.contains("PASSIVE"))
  }

  @Test
  fun `binding is empty before it has run`() {
    // The registry is a bean long before PassiveSkillScriptBinderBootRunner fires; anything reading
    // it in that window must see nothing rather than a half-built map.
    assertEquals(emptyMap<Long, PassiveSkillScript>(), PassiveSkillScriptRegistry(listOf(InnerPeaceStub())).bound())
  }
}
