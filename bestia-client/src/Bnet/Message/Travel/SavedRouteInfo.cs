using Godot;

namespace BestiaBehemothClient.Bnet.Message.Travel
{
  /// <summary>
  /// A way the master keeps, to send its entities along again by name, from the first waypoint to the last.
  /// </summary>
  [GlobalClass]
  public partial class SavedRouteInfo : GodotObject
  {
    [Export] public ulong Id { get; set; }

    [Export] public string Name { get; set; } = "";

    /// <summary>False while the name is the one the server made from where the route starts and ends.</summary>
    [Export] public bool CustomName { get; set; }

    [Export] public Godot.Collections.Array<Vector3> Waypoints { get; set; } = new Godot.Collections.Array<Vector3>();

    public static SavedRouteInfo FromProto(global::Bnet.SavedRoute proto)
    {
      var route = new SavedRouteInfo { Id = proto.Id, Name = proto.Name, CustomName = proto.CustomName };
      foreach (var point in proto.Waypoints)
      {
        route.Waypoints.Add(new Vector3(point.X, point.Z, point.Y));
      }

      return route;
    }
  }
}
