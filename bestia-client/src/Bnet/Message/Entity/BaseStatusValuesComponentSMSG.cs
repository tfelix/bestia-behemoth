using Bnet;
using Godot;

namespace BestiaBehemothClient.Bnet.Message.Entity
{
  /// <summary>
  /// Base status values component message from server: an entity's unbuffed attributes, before
  /// equipment and active status effects are folded in. For a master these are the effort values.
  ///
  /// Distinct from StatusValuesComponentSMSG because the two answer different questions - the
  /// effective values are what the character fights with, these are what the next status point is
  /// priced against.
  /// </summary>
  [GlobalClass]
  public partial class BaseStatusValuesComponentSMSG : EntitySMSG
  {
    [Export] public uint Strength { get; set; }
    [Export] public uint Vitality { get; set; }
    [Export] public uint Intelligence { get; set; }
    [Export] public uint Dexterity { get; set; }
    [Export] public uint Willpower { get; set; }
    [Export] public uint Agility { get; set; }

    public BaseStatusValuesComponentSMSG()
    {
    }

    /// <summary>
    /// Static factory method to create BaseStatusValuesComponentSMSG from protobuf message
    /// </summary>
    /// <param name="protoBaseStatusValues">The protobuf BaseStatusValuesSMSG message from the server</param>
    /// <returns>A new BaseStatusValuesComponentSMSG instance</returns>
    public static BaseStatusValuesComponentSMSG FromProto(global::Bnet.BaseStatusValuesSMSG protoBaseStatusValues)
    {
      return new BaseStatusValuesComponentSMSG()
      {
        EntityId = protoBaseStatusValues.EntityId,
        Strength = protoBaseStatusValues.Strength,
        Vitality = protoBaseStatusValues.Vitality,
        Intelligence = protoBaseStatusValues.Intelligence,
        Dexterity = protoBaseStatusValues.Dexterity,
        Willpower = protoBaseStatusValues.Willpower,
        Agility = protoBaseStatusValues.Agility
      };
    }

    public override string ToString()
    {
      return $"BaseStatusValuesComponentSMSG(EntityId={EntityId}, Strength={Strength}, Vitality={Vitality}, Intelligence={Intelligence}, Dexterity={Dexterity}, Willpower={Willpower}, Agility={Agility})";
    }
  }
}
