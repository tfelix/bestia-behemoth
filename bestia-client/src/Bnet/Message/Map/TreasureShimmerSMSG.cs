using Godot;

namespace BestiaBehemothClient.Bnet.Message.Map
{
  /// <summary>
  /// Treasure Sense found caches under these tiles. The ground above each shimmers until <see cref="Seconds"/>
  /// have passed.
  /// </summary>
  [GlobalClass]
  public partial class TreasureShimmerSMSG : ISMSG
  {
    /// <summary>The ground tile above each cache, in Godot's axes.</summary>
    [Export] public Godot.Collections.Array<Vector3> Spots { get; set; } = [];

    [Export] public float Seconds { get; set; }

    public static TreasureShimmerSMSG FromProto(global::Bnet.TreasureShimmerSMSG proto)
    {
      var message = new TreasureShimmerSMSG { Seconds = proto.Seconds };
      foreach (var spot in proto.Spots)
      {
        // Server z-up to Godot y-up.
        message.Spots.Add(new Vector3(spot.X, spot.Z, spot.Y));
      }

      return message;
    }
  }
}
