package net.bestia.zone.account.master

import jakarta.persistence.AttributeConverter
import jakarta.persistence.Converter
import java.awt.Color

@Converter(autoApply = true)
class ColorHexConverter : AttributeConverter<Color, String> {
  override fun convertToDatabaseColumn(color: Color): String {
    return String.format(
      "%02X%02X%02X",
      color.red,
      color.green,
      color.blue
    )
  }

  override fun convertToEntityAttribute(dbData: String): Color? {
    if (dbData.length != 6) {
      return null
    }

    val r = dbData.substring(0, 2).toInt(16)
    val g = dbData.substring(2, 4).toInt(16)
    val b = dbData.substring(4, 6).toInt(16)

    return Color(r, g, b)
  }
}