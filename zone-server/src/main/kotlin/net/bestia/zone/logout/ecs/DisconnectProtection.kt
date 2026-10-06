package net.bestia.zone.logout.ecs

import net.bestia.zone.ecs.core.Component

/**
 * Keeps the body of a player who disconnected mid-fight in the world for the logout protection time, so pulling
 * the cable is no escape. Unlike [LogoutIntent], damage does not end it, and the client never sees it.
 */
class DisconnectProtection(
  var remainingSeconds: Float
) : Component
