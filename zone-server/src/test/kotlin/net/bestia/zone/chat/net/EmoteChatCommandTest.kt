package net.bestia.zone.chat.net

import io.mockk.mockk
import io.mockk.verify
import net.bestia.zone.ecs.core.WorldView
import net.bestia.zone.entity.ecs.Animation.AnimationKind
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class EmoteChatCommandTest {

  private val world = mockk<WorldView>(relaxed = true)
  private val sut = EmoteChatCommand(world, mockk(relaxed = true))

  @Test
  fun `every emote is a command and listed in help`() {
    AnimationKind.EMOTES.forEach { kind ->
      assertTrue(sut.isMatch("/${kind.command}"), "/${kind.command} should be typed to emote")
      assertTrue(sut.getHelpText().contains("/${kind.command}"), "/help should list /${kind.command}")
    }
  }

  @Test
  fun `the AI's poses and near misses are not commands`() {
    listOf("/idle", "/sleep", "/sitt", "/sit down", "cheer").forEach {
      assertFalse(sut.isMatch(it), "$it should not match")
    }
  }

  @Test
  fun `a typed emote is posted to the tick`() {
    assertTrue(sut.tryExecute(PLAYER_ID, " /cheer ", emptySet()))

    verify(exactly = 1) { world.post(any()) }
  }

  private companion object {
    const val PLAYER_ID = 3L
  }
}
