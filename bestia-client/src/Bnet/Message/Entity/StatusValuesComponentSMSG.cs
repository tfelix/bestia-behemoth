using Bnet;
using Godot;

namespace BestiaBehemothClient.Bnet.Message.Entity
{
  /// <summary>
  /// Status values component message from server containing an entity's current, effective
  /// base status attributes.
  /// </summary>
  [GlobalClass]
  public partial class StatusValuesComponentSMSG : EntitySMSG
  {
    [Export] public uint Strength { get; set; }
    [Export] public uint Vitality { get; set; }
    [Export] public uint Intelligence { get; set; }
    [Export] public uint Dexterity { get; set; }
    [Export] public uint Willpower { get; set; }
    [Export] public uint Agility { get; set; }

    public StatusValuesComponentSMSG()
    {
    }

    /// <summary>
    /// Static factory method to create StatusValuesComponentSMSG from protobuf message
    /// </summary>
    /// <param name="protoStatusValues">The protobuf StatusValuesSMSG message from the server</param>
    /// <returns>A new StatusValuesComponentSMSG instance</returns>
    public static StatusValuesComponentSMSG FromProto(global::Bnet.StatusValuesSMSG protoStatusValues)
    {
      return new StatusValuesComponentSMSG()
      {
        EntityId = protoStatusValues.EntityId,
        Strength = protoStatusValues.Strength,
        Vitality = protoStatusValues.Vitality,
        Intelligence = protoStatusValues.Intelligence,
        Dexterity = protoStatusValues.Dexterity,
        Willpower = protoStatusValues.Willpower,
        Agility = protoStatusValues.Agility
      };
    }

    public override string ToString()
    {
      return $"StatusValuesComponentSMSG(EntityId={EntityId}, Strength={Strength}, Vitality={Vitality}, Intelligence={Intelligence}, Dexterity={Dexterity}, Willpower={Willpower}, Agility={Agility})";
    }
  }
}
