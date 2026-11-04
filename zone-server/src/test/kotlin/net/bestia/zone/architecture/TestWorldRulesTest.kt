package net.bestia.zone.architecture

import com.tngtech.archunit.base.DescribedPredicate.describe
import com.tngtech.archunit.core.domain.JavaMethodCall
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.fields
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses
import net.bestia.worldgen.pipeline.GeneratedWorld
import net.bestia.worldgen.pipeline.StandardWorld
import net.bestia.zone.world.GeneratedWorlds
import net.bestia.zone.world.ReadsLargeWorlds
import net.bestia.zone.world.WorldProvisioningTest
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

/**
 * Tests read generated worlds from `TestWorlds`, which builds each one once and lets it go when memory runs
 * short. A test that builds its own, or pins one in a static field, brings back the full heap these rules exist
 * to prevent.
 */
class TestWorldRulesTest {

  /** Classes whose claim is that two builds agree; a cached world would compare a world with itself. */
  private val buildsTwiceOnPurpose = arrayOf(WorldProvisioningTest::class.java)

  private val buildsAWorld = describe<JavaMethodCall>("StandardWorld.build") { call ->
    call.targetOwner.isEquivalentTo(StandardWorld::class.java) && call.name.startsWith("build")
  }

  @Test
  fun `tests ask TestWorlds for a world instead of building one`() {
    noClasses().that().doNotBelongToAnyOf(*buildsTwiceOnPurpose)
      .should().callMethodWhere(buildsAWorld)
      .check(ZoneClasses.tests)
  }

  private val readsALargeWorld = describe<JavaMethodCall>("GeneratedWorlds.of") { call ->
    call.targetOwner.isEquivalentTo(GeneratedWorlds::class.java) && call.name.startsWith("of")
  }

  /** Unmarked, a class would run outside the group, after the large worlds were let go, and build them again. */
  @Test
  fun `a test that reads a large world is marked so it runs in the group`() {
    val unmarked = ZoneClasses.tests
      .filter { type -> type.methodCallsFromSelf.any { readsALargeWorld.test(it) } }
      .map { type -> generateSequence(type) { it.enclosingClass.orElse(null) }.last() }
      .filterNot { it.isEquivalentTo(GeneratedWorlds::class.java) || it.isAnnotatedWith(ReadsLargeWorlds::class.java) }
      .map { it.simpleName }
      .distinct()

    assertEquals(emptyList(), unmarked)
  }

  @Test
  fun `no test holds a generated world in a static field`() {
    fields().that().haveRawType(GeneratedWorld::class.java)
      .should().notBeStatic()
      .allowEmptyShould(true)
      .check(ZoneClasses.tests)
  }
}
