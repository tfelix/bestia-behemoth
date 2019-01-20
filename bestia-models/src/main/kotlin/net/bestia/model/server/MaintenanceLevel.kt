package net.bestia.model.server

enum class MaintenanceLevel {

  /**
   * Server is operating normally.
   */
  NONE,

  /**
   * User with Super GM level or higher can still login to perform
   * administrative tasks on the live server.
   */
  PARTIAL,

  /**
   * Server is in full maintenance mode. Normal game operation is not
   * possible. Even admins can not login right now.
   */
  FULL
}
