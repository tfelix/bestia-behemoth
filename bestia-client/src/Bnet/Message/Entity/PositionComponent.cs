using Bnet;
using Godot;

namespace BestiaBehemothClient.Bnet.Message.Entity
{
  /// <summary>
  /// Message for updating an entity's position component.
  /// Contains the entity ID and new position data.
  /// </summary>
  [GlobalClass]
  public partial class PositionComponent : EntitySMSG
  {
    [Export]
    public Vector3 Position { get; set; } = Vector3.Zero;

    public static PositionComponent FromProto(global::Bnet.PositionComponent protoPosition)
    {
      return new PositionComponent
      {
        EntityId = protoPosition.EntityId,
        Position = new Vector3(
          protoPosition.Position.X,
          protoPosition.Position.Z,
          protoPosition.Position.Y
        )
      };
    }
  }
}