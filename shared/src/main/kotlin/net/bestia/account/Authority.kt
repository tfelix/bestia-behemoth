package net.bestia.account

enum class Authority {
  /**
   * Special authority set, it means you are a super admin and are allowed to perform
   * ALL actions.
   */
  AUTH_ALL,

  KILL,
  MAP_MOVE
}