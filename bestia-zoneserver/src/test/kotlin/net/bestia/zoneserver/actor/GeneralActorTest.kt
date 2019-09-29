package net.bestia.zoneserver.actor

import akka.actor.AbstractActor
import net.bestia.model.util.PackageLoader
import net.bestia.zoneserver.actor.bootstrap.ClusterBootstrapActor
import net.bestia.zoneserver.actor.entity.EntityActor
import org.junit.Assert
import org.junit.jupiter.api.Test
import java.lang.reflect.Modifier
import java.util.*

/**
 * Especially testing if all actors are annotated correctly.
 *
 * @author Thomas Felix
 */
class GeneralActorTest {

  /**
   * These actors wont be tested for existence of annotations.
   */
  private val whitelist = setOf(
      AwaitResponseActor::class.java
  )

  /**
   * Tests if all actors have the correct spring component annotations.
   */
  @Test
  fun correctSpringAnnotation() {
    val actorLoader = PackageLoader(AbstractActor::class.java,
        "net.bestia.zoneserver.actor")
    val classes = actorLoader.subClasses

    for (clazz in classes) {

      // Ignore abstract classes.
      if (Modifier.isAbstract(clazz.modifiers)) {
        continue
      }

      if (IGNORED_ACTORS.contains(clazz)) {
        continue
      }

      val isAnnotated = clazz.isAnnotationPresent(ActorComponent::class.java) ||
          clazz.isAnnotationPresent(Actor::class.java) ||
          whitelist.contains(clazz)
      Assert.assertTrue("Missing component annotation for: " + clazz.name, isAnnotated)
    }
  }

  /**
   * Implementations should have a public static
   * NAME field.
   */
  @Test
  fun staticNameFieldPresent() {
    val actorLoader = PackageLoader(AbstractActor::class.java,
        "net.bestia.zoneserver.actor")
    val classes = actorLoader.concreteSubClasses.filter {
      !it.isAnnotationPresent(ActorComponent::class.java)
          && !it.isAnnotationPresent(Actor::class.java)
          && !whitelist.contains(it)
    }

    val failedClasses = ArrayList<String>()
    for (clazz in classes) {
      try {
        clazz.getField("NAME")
      } catch (e: Exception) {
        if (!IGNORED_ACTORS.contains(clazz)) {
          failedClasses.add(clazz.canonicalName)
        }
      }
    }

    if (failedClasses.size > 0) {
      Assert.fail(
          "These classes do not implement a public static String NAME field: " + failedClasses.toString())
    }
  }

  companion object {

    private val IGNORED_ACTORS = HashSet<Class<out AbstractActor>>()

    init {
      IGNORED_ACTORS.add(ClusterBootstrapActor::class.java)
      IGNORED_ACTORS.add(EntityActor::class.java)
    }
  }
}
