package net.bestia.zone.chat

/** Which channel a chat line travels on, in both directions. */
enum class ChatType {
  PUBLIC,
  WHISPER,
  PARTY,
  GUILD,
  ERROR,
  COMMAND,
  GM,
  BROADCAST
}
