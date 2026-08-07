package net.bestia.zone.ai.ecs

import net.bestia.zone.ai.core.state.Blackboard
import org.springframework.stereotype.Service
import java.util.concurrent.ConcurrentHashMap

/**
 * Hands out the blackboards that more than one agent shares: one per pack/faction, and one for the whole
 * world.
 *
 * This replaces a stub whose every lookup returned nothing, so the "shared memory" seam existed but no
 * fact ever crossed it. The mechanism that makes sharing work is not here though — it is
 * [net.bestia.zone.ai.core.planner.EffectWriteBack], which cascades a write as far as its key's
 * `MemoryScope` says. All this has to do is make sure two agents in the same pack are handed the *same*
 * board, and everyone the same world board.
 *
 * ### Thread safety
 *
 * The maps are concurrent because agents are created off the tick thread (a spawner running in a message
 * handler) while being read on it, and because the AI systems are meant to become parallel-safe. Note
 * that the [Blackboard]s themselves are not synchronised: they are only ever mutated from AI systems,
 * which the scheduler keeps in their own wave via their declared component writes. A team board written
 * concurrently by two packmates in the same wave would need more than this, and that is a real
 * constraint to respect when the engine's global tick lock is eventually lifted.
 */
@Service
class SharedMemoryService {

  private val teamBoards = ConcurrentHashMap<String, Blackboard>()
  private val world = Blackboard()

  /** The single world-wide board — species-wide knowledge such as which attacks work on what. */
  fun worldBoard(): Blackboard = world

  /**
   * The board shared by everyone in [teamId], created on first use. Null [teamId] means the agent belongs
   * to no pack and gets no shared board, which is different from getting an empty one: a loner must not
   * be handed a board that a later packmate could join.
   */
  fun teamBoard(teamId: String?): Blackboard? =
    teamId?.let { teamBoards.computeIfAbsent(it) { Blackboard() } }

  /** Advances TTL decay on every shared board. Individual boards are ticked with their own agent. */
  fun tick(deltaSeconds: Float) {
    world.tick(deltaSeconds)
    teamBoards.values.forEach { it.tick(deltaSeconds) }
  }
}
