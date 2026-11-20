package net.bestia.zone.prose

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import net.bestia.zone.prose.host.LlamaServerArgs
import net.bestia.zone.prose.host.LlmHost
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TextJobBootRunnerTest {

  private val job = mockk<TextJob>(relaxed = true)
  private val llm = ScriptedLlm("unused")

  private var modelStarts = 0
  private val host = object : LlmHost {
    override fun <T> withModel(work: (LlmClient) -> T): T {
      modelStarts++

      return work(llm)
    }
  }

  @Test
  fun `a zone with the text job off starts no model and plans nothing`() {
    TextJobBootRunner(job, host, ProseConfig(enabled = false)).run()

    assertEquals(0, modelStarts)
    verify(exactly = 0) { job.plan() }
  }

  @Test
  fun `a world that is written boots straight through`() {
    every { job.plan() } returns 0

    TextJobBootRunner(job, host, ProseConfig(enabled = true)).run()

    assertEquals(0, modelStarts)
  }

  @Test
  fun `a world with texts to write gets the model for as long as the job runs`() {
    every { job.plan() } returns 12

    TextJobBootRunner(job, host, ProseConfig(enabled = true)).run()

    assertEquals(1, modelStarts)
    verify { job.run(llm) }
  }

  @Test
  fun `the zone boots when the model cannot be started`() {
    every { job.plan() } returns 12
    val noDocker = object : LlmHost {
      override fun <T> withModel(work: (LlmClient) -> T): T {
        error("no Docker socket")
      }
    }

    TextJobBootRunner(job, noDocker, ProseConfig(enabled = true)).run()
  }

  @Test
  fun `the server is started for one request at a time, without its memory-hungry caches`() {
    val args = LlamaServerArgs.of(ProseConfig.Llm(model = "some/repo-gguf", modelFile = "model_q4.gguf", threads = 6)).joinToString(" ")

    assertTrue("-hf some/repo-gguf -hff model_q4.gguf" in args, args)
    assertTrue("-np 1" in args && "-t 6" in args && "--no-repack" in args && "--cache-ram 0" in args, args)
  }
}
