package net.bestia.zoneserver.chat

import java.lang.reflect.Modifier
import java.util.ArrayList

import org.junit.Assert
import org.junit.Test
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

    Assert.assertEquals("ChatCommands must be annotated with @Component. Not annotated: " + notAnnotated.toString(),
        0, notAnnotated.size.toLong())
  }
}
