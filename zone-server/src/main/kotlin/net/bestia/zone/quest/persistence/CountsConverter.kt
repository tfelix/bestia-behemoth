package net.bestia.zone.quest.persistence

import com.fasterxml.jackson.core.type.TypeReference
import jakarta.persistence.Converter

@Converter
class CountsConverter : JsonListConverter<Int>(object : TypeReference<List<Int>>() {})
