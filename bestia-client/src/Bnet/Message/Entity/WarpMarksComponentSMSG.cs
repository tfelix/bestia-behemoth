using Godot;

namespace BestiaBehemothClient.Bnet.Message.Entity
{
  /// <summary>The spots a Sage saved for Warp Portal, oldest first. Owner-only.</summary>
  [GlobalClass]
  public partial class WarpMarksComponentSMSG : EntitySMSG
  {
    /// <summary>In Godot's axes like every other client position.</summary>
    [Export] public Godot.Collections.Array<Vector3> Spots { get; set; } = [];

    public static WarpMarksComponentSMSG FromProto(ulong entityId, global::Bnet.WarpMarksComponentSMSG proto)
    {
      var message = new WarpMarksComponentSMSG { EntityId = entityId };
      foreach (var spot in proto.Spots)
      {
        // Server z-up to Godot y-up.
        message.Spots.Add(new Vector3(spot.X, spot.Z, spot.Y));
      }

      return message;
    }
  }
}
