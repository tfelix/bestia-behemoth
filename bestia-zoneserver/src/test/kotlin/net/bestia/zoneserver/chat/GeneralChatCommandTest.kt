package net.bestia.zoneserver.chat

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.lang.reflect.Modifier
import java.util.ArrayList

import org.reflections.Reflections
import org.springframework.stereotype.Component

class GeneralChatCommandTest {

  private val reflections = Reflections("net.bestia.zoneserver.chat")

  @Test
  fun allChatCommandsHaveComponentAnnotation() {

    val commands = reflections.getSubTypesOf(BaseChatCommand::class.java)
    val notAnnotated = ArrayList<String>()

    for (clazz in commands) {

      var isAnnotationNeeded = true

      // Not needed if abstract class.
      if (Modifier.isAbstract(clazz.modifiers)) {
        isAnnotationNeeded = false
      }

      // Not needed if SubCommandModule.
      if (SubCommandModule::class.java.isAssignableFrom(clazz)) {
        isAnnotationNeeded = false
      }

      if (isAnnotationNeeded && !clazz.isAnnotationPresent(Component::class.java)) {
        notAnnotated.add(clazz.name)
      }
    }

    assertEquals(0, notAnnotated.size.toLong(), "ChatCommands must be annotated with @Component. Not annotated: $notAnnotated")
  }
}
