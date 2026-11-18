package net.bestia.zone.quest

import net.bestia.bnet.proto.QuestProto
import net.bestia.zone.text.toBnet

fun QuestView.toProto(): QuestProto.Quest {
  val quest = QuestProto.Quest.newBuilder()
    .setQuestId(questId)
    .setOrigin(origin.toProto())
    .setStatus(status.toProto())
    .setRole(role.toProto())
    .setTitle(title.toBnet())
    .setSuggestedLevel(suggestedLevel)
    .setDeadlineEpochMs(deadline.toEpochMilli())
    .setPosterName(posterName)
    .setStage(stage)
  objectives.forEach { quest.addObjectives(it.toProto()) }
  rewards.forEach { quest.addRewards(it.toProto()) }

  return quest.build()
}

private fun QuestView.Objective.toProto(): QuestProto.QuestObjective {
  val objective = QuestProto.QuestObjective.newBuilder()
    .setKind(kind.toProto())
    .setScope(scope.toProto())
    .setItemId(itemId ?: 0)
    .setSpeciesId(speciesId ?: 0)
    .setPlaceName(placeName ?: "")
    .setRequired(required)
    .setProgress(progress)
    .setYours(yours)
    .setStage(stage)
  text?.let { objective.setText(it.toBnet()) }
  choices.forEach { objective.addChoices(QuestProto.QuestChoice.newBuilder().setId(it.id).setText(it.text.toBnet())) }
  objective.setChosen(chosen ?: "")

  return objective.build()
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
    QuestOrigin.WORLD -> QuestProto.QuestOrigin.QUEST_ORIGIN_WORLD
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
    QuestView.Role.OFFERED -> QuestProto.QuestRole.QUEST_ROLE_OFFERED
  }
}

private fun ObjectiveKind.toProto(): QuestProto.ObjectiveKind {
  return when (this) {
    ObjectiveKind.DELIVER_ITEM -> QuestProto.ObjectiveKind.OBJECTIVE_KIND_DELIVER_ITEM
    ObjectiveKind.KILL -> QuestProto.ObjectiveKind.OBJECTIVE_KIND_KILL
    ObjectiveKind.EXAMINE -> QuestProto.ObjectiveKind.OBJECTIVE_KIND_EXAMINE
    ObjectiveKind.TALK -> QuestProto.ObjectiveKind.OBJECTIVE_KIND_TALK
    ObjectiveKind.BRING -> QuestProto.ObjectiveKind.OBJECTIVE_KIND_BRING
    ObjectiveKind.DECIDE -> QuestProto.ObjectiveKind.OBJECTIVE_KIND_DECIDE
  }
}

private fun ObjectiveScope.toProto(): QuestProto.ObjectiveScope {
  return when (this) {
    ObjectiveScope.SHARED -> QuestProto.ObjectiveScope.OBJECTIVE_SCOPE_SHARED
    ObjectiveScope.PERSONAL -> QuestProto.ObjectiveScope.OBJECTIVE_SCOPE_PERSONAL
  }
}
