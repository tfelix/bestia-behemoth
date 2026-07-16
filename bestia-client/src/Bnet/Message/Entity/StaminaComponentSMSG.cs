using Bnet;
using Godot;

namespace BestiaBehemothClient.Bnet.Message.Entity
{
  /// <summary>
  /// Stamina component message from server containing entity stamina information.
  /// </summary>
  [GlobalClass]
  public partial class StaminaComponentSMSG : EntitySMSG
  {
    [Export] public uint Current { get; set; }
    [Export] public uint Max { get; set; }

    public StaminaComponentSMSG()
    {
    }

    /// <summary>
    /// Static factory method to create StaminaComponentSMSG from protobuf message
    /// </summary>
    /// <param name="protoStaminaComponent">The protobuf StaminaComponentSMSG message from the server</param>
    /// <returns>A new StaminaComponentSMSG instance</returns>
    public static StaminaComponentSMSG FromProto(global::Bnet.StaminaComponentSMSG protoStaminaComponent)
    {
      return new StaminaComponentSMSG()
      {
        EntityId = protoStaminaComponent.EntityId,
        Current = protoStaminaComponent.Current,
        Max = protoStaminaComponent.Max
      };
    }

    public override string ToString()
    {
      return $"StaminaComponentSMSG(EntityId={EntityId}, Current={Current}, Max={Max})";
    }
  }
}
