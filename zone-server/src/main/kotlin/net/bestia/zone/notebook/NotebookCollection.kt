package net.bestia.zone.notebook

/**
 * One kind of thing a player keeps in their notebook: saved routes, map marks, map preferences - and later
 * settings to share between the game and the companion, such as shortcut bars or key bindings. A Spring bean;
 * [NotebookService] finds every one by its [id].
 *
 * The notebook stores each entry as JSON and knows nothing of what is in it. This class is what does: [type] is
 * what a body is read as, [validate] what a body may be, [present] what clients are shown of it. Settings that
 * belong to one device rather than to the player - graphics, window, audio - do not belong here.
 */
abstract class NotebookCollection<T : Any>(
  val id: String,
  val type: Class<T>,
  val scope: Scope,
  val shape: Shape,
  val maxEntries: Int,
  val maxBodyBytes: Int,
  private val access: Map<Client, Access>,
) {

  /** Whose an entry is: the character's, so each master has their own, or the player's, shared by all of them. */
  enum class Scope { MASTER, ACCOUNT }

  /** [KEYED] holds many entries, each under its own key; [SINGLE] one document under [SINGLE_KEY]. */
  enum class Shape { KEYED, SINGLE }

  /** Which app is asking. The socket is the game, the bus is the companion. */
  enum class Client { GAME, COMPANION }

  enum class Access { NONE, READ, WRITE }

  fun mayRead(client: Client): Boolean {
    return access[client] != null && access[client] != Access.NONE
  }

  fun mayWrite(client: Client): Boolean {
    return access[client] == Access.WRITE
  }

  /** Null when [value] may be kept, else why not, for the log. */
  open fun validate(value: T): String? {
    return null
  }

  /** [value] as clients are shown it, with anything derived filled in. */
  open fun present(value: T): Any {
    return value
  }

  companion object {
    const val SINGLE_KEY = "_"
  }
}
