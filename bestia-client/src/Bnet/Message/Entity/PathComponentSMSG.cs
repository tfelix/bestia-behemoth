using Godot;

namespace BestiaBehemothClient.Bnet.Message.Entity
{
  /// <summary>
  /// Message for updating an entity's path component.
  /// Contains the entity ID and path data as a list of Vector3 positions.
  /// </summary>
  [GlobalClass]
  public partial class PathComponentSMSG : EntitySMSG
  {
    [Export]
    public Godot.Collections.Array<Vector3> Path { get; set; } = new Godot.Collections.Array<Vector3>();

    public static PathComponentSMSG FromProto(global::Bnet.PathComponentSMSG protoPath)
    {
      var pathComponent = new PathComponentSMSG
      {
        EntityId = protoPath.EntityId,
        Path = new Godot.Collections.Array<Vector3>()
      };

      foreach (var pathPoint in protoPath.Path)
      {
        pathComponent.Path.Add(new Vector3(
          pathPoint.X,
          pathPoint.Z,
          pathPoint.Y
        ));
      }

      return pathComponent;
    }
  }
}
