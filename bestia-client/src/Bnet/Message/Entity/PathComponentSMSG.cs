using Godot;

namespace BestiaBehemothClient.Bnet.Message.Entity
{
  /// <summary>
  /// The waypoints an entity is walking, as Vector3 positions in Godot's axis order.
  /// <para>
  /// Sent once per walk, not per tile step - the client follows the waypoints itself with the same
  /// arithmetic the server's MoveSystem uses (see Entity's movement prediction header).
  /// </para>
  /// <para>
  /// An empty <see cref="Path"/> is the stop notification, and then <see cref="StopPosition"/> says
  /// where the entity actually halted. Guard on <see cref="HasStopPosition"/>: it is absent for an
  /// entity the server has no position for.
  /// </para>
  /// </summary>
  [GlobalClass]
  public partial class PathComponentSMSG : EntitySMSG
  {
    [Export]
    public Godot.Collections.Array<Vector3> Path { get; set; } = new Godot.Collections.Array<Vector3>();

    /// <summary>Where the entity stopped; only meaningful when <see cref="HasStopPosition"/> is true.</summary>
    [Export]
    public Vector3 StopPosition { get; set; } = Vector3.Zero;

    /// <summary>Whether the server told us where the entity stopped, as opposed to leaving it to our prediction.</summary>
    [Export]
    public bool HasStopPosition { get; set; }

    public static PathComponentSMSG FromProto(global::Bnet.PathComponentSMSG protoPath)
    {
      var pathComponent = new PathComponentSMSG
      {
        EntityId = protoPath.EntityId,
        Path = new Godot.Collections.Array<Vector3>(),
        HasStopPosition = protoPath.StopPosition != null
      };

      foreach (var pathPoint in protoPath.Path)
      {
        pathComponent.Path.Add(ToGodot(pathPoint));
      }

      if (protoPath.StopPosition != null)
      {
        pathComponent.StopPosition = ToGodot(protoPath.StopPosition);
      }

      return pathComponent;
    }

    private static Vector3 ToGodot(global::Bnet.Vec3 vec) => new Vector3(vec.X, vec.Z, vec.Y);
  }
}
