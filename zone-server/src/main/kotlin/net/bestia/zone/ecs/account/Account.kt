package net.bestia.zone.ecs.account

import net.bestia.zone.ecs.core.Component

data class Account(
  var accountId: Long,

  /**
   * Whether this master has completed the Master Ritual (see
   * [net.bestia.zone.account.master.Master.hasPerformedMasterRitual], which this mirrors live
   * while the master is online) and may therefore invest skill points outside the Novice tree -
   * see [net.bestia.zone.account.master.skill.MasterSkillTreeService]. Not pushed to the client
   * as its own component; the client learns the current value from `SelfSMSG` (sent whenever a
   * `GetSelfCMSG` is handled, e.g. on login).
   */
  var hasPerformedMasterRitual: Boolean = false,
) : Component