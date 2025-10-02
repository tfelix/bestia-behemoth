using Bnet;
using Godot;

namespace BestiaBehemothClient.Bnet.Message.Entity
{
  /// <summary>
  /// Health component message from server containing entity health information.
  /// </summary>
  [GlobalClass]
  public partial class HealthComponentSMSG : ISMSG
  {
    [Export] public ulong EntityId { get; set; }
    [Export] public uint Current { get; set; }
    [Export] public uint Max { get; set; }

    public HealthComponentSMSG()
    {
    }

    public HealthComponentSMSG(global::Bnet.HealthComponentSMSG healthComponent)
    {
      EntityId = healthComponent.EntityId;
      Current = healthComponent.Current;
      Max = healthComponent.Max;
    }

    /// <summary>
    /// Static factory method to create HealthComponentSMSG from protobuf message
    /// </summary>
    /// <param name="protoHealthComponent">The protobuf HealthComponentSMSG message from the server</param>
    /// <returns>A new HealthComponentSMSG instance</returns>
    public static HealthComponentSMSG FromProto(global::Bnet.HealthComponentSMSG protoHealthComponent)
    {
      return new HealthComponentSMSG()
      {
        EntityId = protoHealthComponent.EntityId,
        Current = protoHealthComponent.Current,
        Max = protoHealthComponent.Max
      };
    }

    public override string ToString()
    {
      return $"HealthComponentSMSG(EntityId={EntityId}, Current={Current}, Max={Max})";
    }
  }
}
