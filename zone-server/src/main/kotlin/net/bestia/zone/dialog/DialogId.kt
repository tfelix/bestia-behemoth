package net.bestia.zone.dialog

/**
 * Every dialog the server can send, by name. This is what call sites use - `dialogService.send(
 * accountId, DialogId.MASTER_INTRO, ...)` - so picking a message is a symbol lookup with
 * autocompletion rather than a magic number.
 *
 * Must stay in lockstep with `dialogs.yml`, which is the source of truth for the id's metadata
 * (type, declared placeholders). [DialogCatalogBootValidator] fails the boot if an entry exists on
 * only one side, so the duplication cannot silently rot.
 */
enum class DialogId(val id: Int) {
  MASTER_INTRO(1),
  EXAMPLE_NPC_GREETING(2);

  companion object {
    private val byId = entries.associateBy { it.id }

    fun findById(id: Int): DialogId? = byId[id]
  }
}
