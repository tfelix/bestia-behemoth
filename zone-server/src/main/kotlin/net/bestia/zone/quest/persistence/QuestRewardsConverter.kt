package net.bestia.zone.quest.persistence

import com.fasterxml.jackson.core.type.TypeReference
import jakarta.persistence.Converter
import net.bestia.zone.quest.QuestReward

@Converter
class QuestRewardsConverter : JsonListConverter<QuestReward>(object : TypeReference<List<QuestReward>>() {})
