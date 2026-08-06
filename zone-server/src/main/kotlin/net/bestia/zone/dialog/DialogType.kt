package net.bestia.zone.dialog

/**
 * What the client is expected to do with a dialog. Only [CONFIRM] exists today; querying the player
 * for an answer (a `CHOICE` dialog) is planned, and the wire format is shaped so adding it needs
 * only a new value here, a new proto enum value, and a CMSG carrying the answer back.
 */
enum class DialogType {
  /** A plain "read the text and confirm" popup. Nothing is sent back to the server. */
  CONFIRM
}
