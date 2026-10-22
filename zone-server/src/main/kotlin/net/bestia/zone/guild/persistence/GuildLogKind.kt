package net.bestia.zone.guild.persistence

/** What a [GuildLogEntry] records. The client words each one; see `GUILD_LOG_*` in `general.csv`. */
enum class GuildLogKind {
  FOUNDED,
  JOINED,
  LEFT,
  KICKED,
  RANK_SET,
  RANK_ADDED,
  RANK_EDITED,
  RANK_DELETED,
  SKILL_LEARNED,
  MOTD_CHANGED,
  EMBLEM_CHANGED,
}
