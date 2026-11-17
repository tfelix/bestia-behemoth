package net.bestia.zone.quest.net

import net.bestia.bnet.proto.QuestProto
import net.bestia.zone.quest.ObjectiveKind
import net.bestia.zone.quest.ObjectiveScope
import net.bestia.zone.quest.QuestOrigin
import net.bestia.zone.quest.QuestReward
import net.bestia.zone.quest.QuestStatus
import net.bestia.zone.quest.QuestTextArg
import net.bestia.zone.quest.QuestView

internal fun QuestView.toProto(): QuestProto.Quest {
  val quest = QuestProto.Quest.newBuilder()
    .setQuestId(questId)
    .setOrigin(origin.toProto())
    .setStatus(status.toProto())
    .setRole(role.toProto())
    .setTitleKey(titleKey)
    .setSuggestedLevel(suggestedLevel)
    .setDeadlineEpochMs(deadline.toEpochMilli())
    .setPosterName(posterName)
  textArgs.forEach { quest.putArgs(it.name, it.toProto()) }
  objectives.forEach { quest.addObjectives(it.toProto()) }
  rewards.forEach { quest.addRewards(it.toProto()) }

  return quest.build()
}

private fun QuestView.Objective.toProto(): QuestProto.QuestObjective {
  return QuestProto.QuestObjective.newBuilder()
    .setKind(kind.toProto())
    .setScope(scope.toProto())
    .setItemId(itemId ?: 0)
    .setRequired(required)
    .setProgress(progress)
    .setYours(yours)
    .build()
}

private fun QuestTextArg.toProto(): QuestProto.QuestTextArg {
  val arg = QuestProto.QuestTextArg.newBuilder()
  when (kind) {
    QuestTextArg.Kind.NUMBER -> arg.setNumber(value.toLong())
    QuestTextArg.Kind.ITEM -> arg.setItemId(value.toLong())
    QuestTextArg.Kind.SPECIES -> arg.setSpeciesId(value.toLong())
    QuestTextArg.Kind.TOKEN -> arg.setToken(value)
    QuestTextArg.Kind.NAME -> arg.setName(value)
  }

  return arg.build()
}

private fun QuestReward.toProto(): QuestProto.QuestReward {
  val protoKind = when (kind) {
    QuestReward.Kind.COINS_PER_UNIT -> QuestProto.QuestRewardKind.QUEST_REWARD_KIND_COINS_PER_UNIT
  }

  return QuestProto.QuestReward.newBuilder().setKind(protoKind).setAmount(amount).build()
}

private fun QuestOrigin.toProto(): QuestProto.QuestOrigin {
  return when (this) {
    QuestOrigin.BLACKBOARD -> QuestProto.QuestOrigin.QUEST_ORIGIN_BLACKBOARD
  }
}

private fun QuestStatus.toProto(): QuestProto.QuestStatus {
  return when (this) {
    QuestStatus.OPEN -> QuestProto.QuestStatus.QUEST_STATUS_OPEN
    QuestStatus.CLOSING -> QuestProto.QuestStatus.QUEST_STATUS_CLOSING
    QuestStatus.COMPLETED -> QuestProto.QuestStatus.QUEST_STATUS_COMPLETED
    QuestStatus.EXPIRED -> QuestProto.QuestStatus.QUEST_STATUS_EXPIRED
    QuestStatus.WITHDRAWN -> QuestProto.QuestStatus.QUEST_STATUS_WITHDRAWN
  }
}

private fun QuestView.Role.toProto(): QuestProto.QuestRole {
  return when (this) {
    QuestView.Role.ACCEPTED -> QuestProto.QuestRole.QUEST_ROLE_ACCEPTED
    QuestView.Role.POSTED -> QuestProto.QuestRole.QUEST_ROLE_POSTED
  }
}

private fun ObjectiveKind.toProto(): QuestProto.ObjectiveKind {
  return when (this) {
    ObjectiveKind.DELIVER_ITEM -> QuestProto.ObjectiveKind.OBJECTIVE_KIND_DELIVER_ITEM
  }
}

private fun ObjectiveScope.toProto(): QuestProto.ObjectiveScope {
  return when (this) {
    ObjectiveScope.SHARED -> QuestProto.ObjectiveScope.OBJECTIVE_SCOPE_SHARED
    ObjectiveScope.PERSONAL -> QuestProto.ObjectiveScope.OBJECTIVE_SCOPE_PERSONAL
  }
}
