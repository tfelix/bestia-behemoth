using Bnet;
using Godot;

namespace BestiaBehemothClient.Bnet.Message.Entity
{
  /// <summary>
  /// Message for updating an entity's speed component.
  /// Contains the entity ID and speed value.
  /// </summary>
  [GlobalClass]
  public partial class SpeedComponentSMSG : EntitySMSG
  {
    [Export]
    public float Speed { get; set; } = 1.0f;

    public static SpeedComponentSMSG FromProto(ulong entityId, global::Bnet.SpeedComponentSMSG protoSpeed)
    {
      return new SpeedComponentSMSG
      {
        EntityId = entityId,
        Speed = protoSpeed.Speed
      };
    }
  }
}
