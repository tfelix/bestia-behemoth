using Godot;

namespace BestiaBehemothClient.Bnet.Message.Postal
{
  /// <summary>A parcel to the played master, on the road or waiting at its office.</summary>
  /// <remarks>Its own file because Godot registers one <c>[GlobalClass]</c> per C# file, see <c>ShopEntry</c>.</remarks>
  [GlobalClass]
  public partial class ParcelEntry : GodotObject
  {
    [Export] public ulong ParcelId { get; set; }
    [Export] public string SenderName { get; set; } = "";
    [Export] public string DestinationName { get; set; } = "";
    [Export] public long LatestArrivalEpochSeconds { get; set; }
    [Export] public bool Arrived { get; set; }

    /// <summary>Whether the office the window is open at may hand it out.</summary>
    [Export] public bool CollectableHere { get; set; }

    /// <summary>On its way back to its sender.</summary>
    [Export] public bool Returning { get; set; }
    [Export] public Godot.Collections.Array<ParcelGoodsEntry> Goods { get; set; } = [];

    public static ParcelEntry FromProto(global::Bnet.Parcel proto)
    {
      var goods = new Godot.Collections.Array<ParcelGoodsEntry>();
      foreach (var line in proto.Goods)
      {
        goods.Add(ParcelGoodsEntry.FromProto(line));
      }

      return new ParcelEntry
      {
        ParcelId = proto.ParcelId,
        SenderName = proto.SenderName,
        DestinationName = proto.DestinationName,
        LatestArrivalEpochSeconds = proto.LatestArrivalEpochSeconds,
        Arrived = proto.Arrived,
        CollectableHere = proto.CollectableHere,
        Returning = proto.Returning,
        Goods = goods
      };
    }
  }
}
