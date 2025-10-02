using Bnet;
using Godot;

namespace BestiaBehemothClient.Bnet.Message.Entity
{
  /// <summary>
  /// Mana component message from server containing entity mana information.
  /// </summary>
  [GlobalClass]
  public partial class ManaComponentSMSG : EntitySMSG
  {
    [Export] public uint Current { get; set; }
    [Export] public uint Max { get; set; }

    public ManaComponentSMSG()
    {
    }

    /// <summary>
    /// Static factory method to create ManaComponentSMSG from protobuf message
    /// </summary>
    /// <param name="protoManaComponent">The protobuf ManaComponentSMSG message from the server</param>
    /// <returns>A new ManaComponentSMSG instance</returns>
    public static ManaComponentSMSG FromProto(global::Bnet.ManaComponentSMSG protoManaComponent)
    {
      return new ManaComponentSMSG()
      {
        EntityId = protoManaComponent.EntityId,
        Current = protoManaComponent.Current,
        Max = protoManaComponent.Max
      };
    }

    public override string ToString()
    {
      return $"ManaComponentSMSG(EntityId={EntityId}, Current={Current}, Max={Max})";
    }
  }
}
