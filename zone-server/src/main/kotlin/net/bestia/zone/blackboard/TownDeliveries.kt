package net.bestia.zone.blackboard

import net.bestia.bnet.proto.OperationErrorProto.OpError
import net.bestia.zone.quest.QuestView

/**
 * Takes the goods a town asked for on the board. A town has no mailbox and no posting: who posts for the towns
 * also knows where their goods go and what they pay.
 */
interface TownDeliveries {

  /** @return the refusal, or null when up to [amount] of what [quest] wants was handed in */
  fun handIn(visit: BoardVisit, quest: QuestView, amount: Int): OpError?
}
