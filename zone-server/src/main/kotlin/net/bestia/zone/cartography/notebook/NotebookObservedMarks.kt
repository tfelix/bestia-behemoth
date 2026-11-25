package net.bestia.zone.cartography.notebook

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import net.bestia.zone.casting.sense.ObservedMarks
import net.bestia.zone.notebook.NotebookCollection.Client
import net.bestia.zone.notebook.NotebookService
import net.bestia.zone.persistence.AsyncJobExecutor
import org.springframework.stereotype.Component

/** Writes Observation's marks into the master's [MarksCollection], off the tick like any notebook entry. */
@Component
class NotebookObservedMarks(
  private val notebook: NotebookService,
  private val jobs: AsyncJobExecutor,
) : ObservedMarks {

  private val mapper = jacksonObjectMapper()

  override fun record(accountId: Long, masterId: Long, observed: List<ObservedMarks.Observed>) {
    val owner = NotebookService.Owner(accountId, masterId)
    val marks = observed.take(MAX_MARKS)

    jobs.submit(masterId) {
      val listed = notebook.list(owner, Client.GAME, MarksCollection.ID) as? NotebookService.Result.Listed ?: return@submit
      listed.entries.filter { it.key.startsWith(KEY_PREFIX) }
        .forEach { notebook.delete(owner, Client.GAME, MarksCollection.ID, it.key, baseRevision = null) }

      marks.forEachIndexed { i, mark ->
        val body = mapper.writeValueAsString(
          MarksCollection.Mark(mark.name.take(MarksCollection.MAX_NAME_LENGTH), mark.east, mark.north, ICON, mark.note)
        )
        notebook.put(owner, Client.GAME, MarksCollection.ID, "$KEY_PREFIX$i", body, baseRevision = null)
      }
    }
  }

  private companion object {
    const val KEY_PREFIX = "observed-"
    const val ICON = "danger"
    const val MAX_MARKS = 10
  }
}
