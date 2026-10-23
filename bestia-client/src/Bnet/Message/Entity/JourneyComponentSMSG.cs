using Godot;

namespace BestiaBehemothClient.Bnet.Message.Entity
{
  /// <summary>
  /// Where one of the player's own entities is travelling, for its map; owner-only, at any distance.
  /// </summary>
  [GlobalClass]
  public partial class JourneyComponentSMSG : EntitySMSG
  {
    [Export] public Godot.Collections.Array<Vector3> Waypoints { get; set; } = new Godot.Collections.Array<Vector3>();

    /// <summary>The waypoint travelled to now; the ones before it are behind.</summary>
    [Export] public int NextIndex { get; set; }

    [Export] public string Name { get; set; } = "";

    /// <summary>The way still ahead as planned, coarsely: road bends and junctions, then the waypoints.</summary>
    [Export] public Godot.Collections.Array<Vector3> Route { get; set; } = new Godot.Collections.Array<Vector3>();

    [Export] public bool Removed { get; set; }

    public static JourneyComponentSMSG FromProto(ulong entityId, global::Bnet.JourneyComponentSMSG proto)
    {
      var journey = new JourneyComponentSMSG
      {
        EntityId = entityId,
        NextIndex = (int)proto.NextIndex,
        Name = proto.Name,
        Removed = proto.Removed
      };

      foreach (var waypoint in proto.Waypoints)
      {
        journey.Waypoints.Add(ToGodot(waypoint));
      }

      foreach (var point in proto.Route)
      {
        journey.Route.Add(ToGodot(point));
      }

      return journey;
    }

    private static Vector3 ToGodot(global::Bnet.Vec3 vec) => new Vector3(vec.X, vec.Z, vec.Y);

    public override string ToString() =>
      $"JourneyComponentSMSG(EntityId={EntityId}, Waypoints={Waypoints.Count}, NextIndex={NextIndex}, Removed={Removed})";
  }
}
