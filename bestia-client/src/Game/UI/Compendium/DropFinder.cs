using System.Linq;
using Godot;
using Godot.Collections;

namespace BestiaBehemothClient.Game.UI.Compendium
{
  /// <summary>
  /// Hands <see cref="DropLookup"/> to GDScript, which cannot call a static C# method. A drop comes back as a
  /// dictionary with an <c>id</c> (item or species) and a <c>chance</c>.
  /// </summary>
  [GlobalClass]
  public partial class DropFinder : RefCounted
  {
    /// <summary>The items <paramref name="species"/> (a <c>BestiaResource</c>) drops, likeliest first.</summary>
    public Array<Dictionary> DropsOf(Resource species)
    {
      return ToGodot(DropLookup.DropsOf(LootOf(species)));
    }

    /// <summary>Which of <paramref name="species"/> (<c>BestiaResource</c>s) drop the item, likeliest first.</summary>
    public Array<Dictionary> DroppedBy(Array species, int itemId)
    {
      return ToGodot(DropLookup.DroppedBy(species.Select(one => LootOf(one.As<Resource>())), itemId));
    }

    public string ChanceLabel(int chance)
    {
      return DropLookup.ChanceLabel(chance);
    }

    private static SpeciesLoot LootOf(Resource species)
    {
      return new SpeciesLoot(
        species.Get("bestia_id").AsInt32(),
        species.Get("loot_item_ids").AsInt32Array(),
        species.Get("loot_chances").AsInt32Array());
    }

    private static Array<Dictionary> ToGodot(System.Collections.Generic.IEnumerable<Drop> drops)
    {
      return new Array<Dictionary>(drops.Select(drop => new Dictionary { { "id", drop.Id }, { "chance", drop.Chance } }));
    }
  }
}
