package net.bestia.zone.dialog

/**
 * Static, in-memory definition of one dialog: which id the client resolves its text under, what
 * kind of dialog it is, and which placeholders its text may use. Loaded from `dialogs.yml` at boot
 * by [net.bestia.zone.boot.DialogImporterBootRunner] into [DialogDefinitionRegistry] - this is
 * config, not player state, so it is never persisted to the database (same shape as
 * [net.bestia.zone.battle.status.StatusEffectDefinition]).
 *
 * Note there is deliberately no text field: the wire carries only [id] and the client looks the
 * translation up itself.
 */
data class DialogDefinition(
  val id: Int,
  val identifier: String,
  val type: DialogType,
  /**
   * Names of the placeholders the translated text may contain, e.g. `masterName` for
   * `{masterName}`. [DialogService] requires a send to supply exactly these keys.
   */
  val args: List<String>,
)
