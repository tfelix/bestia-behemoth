using Godot;

namespace BestiaBehemothClient.Bnet.Message.Entity
{
  /// <summary>
  /// One stat a copy of an item rolled, such as +3 STR. <see cref="Stat"/> is the server's stat name, which
  /// is also the suffix of its <c>ITEM_STAT_</c> translation key.
  /// </summary>
  [GlobalClass]
  public partial class ItemStatValue : RefCounted
  {
    [Export] public string Stat { get; set; } = "";
    [Export] public int Value { get; set; }

    public static Godot.Collections.Array<ItemStatValue> FromProto(
      Google.Protobuf.Collections.RepeatedField<global::Bnet.ItemStatValue> protoStats)
    {
      var stats = new Godot.Collections.Array<ItemStatValue>();
      foreach (var protoStat in protoStats)
      {
        stats.Add(new ItemStatValue { Stat = protoStat.Stat, Value = protoStat.Value });
      }

      return stats;
    }
  }
}
