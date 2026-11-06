using Godot;

namespace BestiaBehemothClient.Bnet.Message.Postal
{
  /// <summary>A sealed parcel on the route board: where it goes, what it weighs and pays. Never what is inside.</summary>
  /// <remarks>Its own file because Godot registers one <c>[GlobalClass]</c> per C# file, see <c>ShopEntry</c>.</remarks>
  [GlobalClass]
  public partial class RouteBoardEntry : GodotObject
  {
    [Export] public ulong ParcelId { get; set; }
    [Export] public string DestinationName { get; set; } = "";

    /// <summary>In item weight units, 100 per kg.</summary>
    [Export] public int Weight { get; set; }
    [Export] public long Pay { get; set; }
    [Export] public long LatestArrivalEpochSeconds { get; set; }

    public static RouteBoardEntry FromProto(global::Bnet.RouteBoardParcel proto)
    {
      return new RouteBoardEntry
      {
        ParcelId = proto.ParcelId,
        DestinationName = proto.DestinationName,
        Weight = proto.Weight,
        Pay = proto.Pay,
        LatestArrivalEpochSeconds = proto.LatestArrivalEpochSeconds
      };
    }
  }
}
