using Godot;
using Godot.Collections;

namespace BestiaBehemothClient.Bnet.Message.Master
{
  /// <summary>
  /// The orders in force for one of the player's bestias: the answer to <see cref="GetBestiaOrdersCMSG"/>, and sent
  /// whenever they change, wherever the change came from.
  /// </summary>
  [GlobalClass]
  public partial class BestiaAiConfigSMSG : ISMSG
  {
    [Export] public ulong PlayerBestiaId { get; set; }
    /// <summary>What is in force: a preset's orders with the bestia's own numbers on top.</summary>
    [Export] public OrderSheet Orders { get; set; }
    /// <summary>0 when the orders are the bestia's own.</summary>
    [Export] public ulong PresetId { get; set; }
    /// <summary>The numbers the bestia keeps while it follows a preset.</summary>
    [Export] public Dictionary OwnOverrides { get; set; } = new();

    public static BestiaAiConfigSMSG FromProto(global::Bnet.BestiaAiConfigSMSG proto)
    {
      return new BestiaAiConfigSMSG
      {
        PlayerBestiaId = proto.PlayerBestiaId,
        Orders = OrderSheet.FromProto(proto),
        PresetId = proto.HasPresetId ? proto.PresetId : 0,
        OwnOverrides = OrderSheet.OverridesFromProto(proto.OwnOverrides),
      };
    }
  }
}
