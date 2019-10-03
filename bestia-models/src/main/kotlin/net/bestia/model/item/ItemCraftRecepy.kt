package net.bestia.model.item

import net.bestia.model.AbstractEntity
import javax.persistence.AttributeConverter
import javax.persistence.Convert
import javax.persistence.Entity

@Entity
class ItemCraftRecipe(
    @get:Convert(converter = RecipeConverter::class)
    val recipe: ResourceMatrix
) : AbstractEntity()

class RecipeConverter : AttributeConverter<ResourceMatrix, String> {
  override fun convertToDatabaseColumn(recipe: ResourceMatrix): String {
    return ResourceMatrix.MAPPER.writeValueAsString(recipe)
  }

  override fun convertToEntityAttribute(recipeJson: String): ResourceMatrix {
    return ResourceMatrix.MAPPER.readValue(recipeJson, ResourceMatrix::class.java)
  }
}