package net.bestia.zoneserver.actor.entity.component

import net.bestia.zoneserver.actor.ActorComponent
import net.bestia.zoneserver.entity.component.Component
import org.junit.jupiter.api.Test
import org.reflections.Reflections
import org.springframework.test.util.AssertionErrors.assertEquals

class EntityComponentActorFactoryTest {

  @Test
  fun `every component has an annotated actor`() {
    val components = getComponentClasses()
    val handledByActors = getComponentsHandledByActors()

    val notHandledComponents = components - handledByActors

    assertEquals(
        "Add ComponentActors annotated with @ActorComponent(COMPONENT::class) for the missing components",
        emptySet<String>(),
        notHandledComponents.asSequence().map { it.simpleName }.toSet()
    )
  }

  private fun getComponentClasses(): Set<Class<out Component>> {
    val reflections = Reflections("net.bestia.zoneserver.entity.component")
    val components = reflections.getSubTypesOf(Component::class.java)
    return components.toSet()
  }

  private fun getComponentsHandledByActors(): Set<Class<out Component>> {
    val reflections = Reflections("net.bestia.zoneserver.actor")
    val annotatedActors = reflections.getTypesAnnotatedWith(ActorComponent::class.java)
    return annotatedActors.asSequence()
        .map { it.getAnnotation(ActorComponent::class.java).component.java }
        .toSet()
  }
}