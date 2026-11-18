package net.bestia.zone.quest.persistence

import com.fasterxml.jackson.core.type.TypeReference
import jakarta.persistence.Converter

@Converter
class ChoicesConverter : JsonListConverter<String>(object : TypeReference<List<String>>() {})
