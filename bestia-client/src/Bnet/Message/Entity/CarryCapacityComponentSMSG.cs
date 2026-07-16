using Bnet;
using Godot;

namespace BestiaBehemothClient.Bnet.Message.Entity
{
  /// <summary>
  /// Carry capacity component message from server containing entity inventory weight information.
  /// </summary>
  [GlobalClass]
  public partial class CarryCapacityComponentSMSG : EntitySMSG
  {
    [Export] public uint Current { get; set; }
    [Export] public uint Max { get; set; }

    public CarryCapacityComponentSMSG()
    {
    }

    /// <summary>
    /// Static factory method to create CarryCapacityComponentSMSG from protobuf message
    /// </summary>
    /// <param name="protoCarryCapacityComponent">The protobuf CarryCapacityComponentSMSG message from the server</param>
    /// <returns>A new CarryCapacityComponentSMSG instance</returns>
    public static CarryCapacityComponentSMSG FromProto(global::Bnet.CarryCapacityComponentSMSG protoCarryCapacityComponent)
    {
      return new CarryCapacityComponentSMSG()
      {
        EntityId = protoCarryCapacityComponent.EntityId,
        Current = protoCarryCapacityComponent.Current,
        Max = protoCarryCapacityComponent.Max
      };
    }

    public override string ToString()
    {
      return $"CarryCapacityComponentSMSG(EntityId={EntityId}, Current={Current}, Max={Max})";
    }
  }
}
