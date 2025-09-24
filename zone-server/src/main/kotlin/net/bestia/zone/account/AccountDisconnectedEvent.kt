package net.bestia.zone.account

import net.bestia.zone.BestiaEvent

class AccountDisconnectedEvent(source: Any, val accountId: Long) : BestiaEvent(source)