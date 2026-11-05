using System.Collections.Generic;
using System.Linq;

namespace BestiaBehemothClient.Bnet.Message.Postal
{
  /// <summary>The lines of a parcel as GDScript holds them: ids and amounts side by side.</summary>
  public static class ParcelItems
  {
    public static IEnumerable<global::Bnet.ParcelItem> Of(
      Godot.Collections.Array<long> playerItemIds,
      Godot.Collections.Array<int> amounts)
    {
      return playerItemIds.Select((id, i) => new global::Bnet.ParcelItem { PlayerItemId = (ulong)id, Amount = amounts[i] });
    }
  }
}
