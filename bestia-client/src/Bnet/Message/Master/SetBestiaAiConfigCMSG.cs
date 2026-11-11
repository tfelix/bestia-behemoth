using Bnet;
using Godot.Collections;

namespace BestiaBehemothClient.Bnet.Message.Master
{
  /// <summary>
  /// Gives one of the player's bestias new orders. With a <see cref="PresetId"/> it follows that preset, and only
  /// <see cref="OwnOverrides"/> of the orders are its own.
  /// </summary>
  public partial class SetBestiaAiConfigCMSG : ICMSG
  {
    public ulong PlayerBestiaId { get; set; }
    public OrderSheet Orders { get; set; }
    /// <summary>0 for orders of its own.</summary>
    public ulong PresetId { get; set; }
    /// <summary>Only read while it follows a preset.</summary>
    public Dictionary OwnOverrides { get; set; } = new();

    public override Envelope ToEnvelope()
    {
      var following = PresetId != 0;
      var msg = new global::Bnet.SetBestiaAiConfigCMSG
      {
        PlayerBestiaId = PlayerBestiaId,
        Job = (global::Bnet.SetBestiaAiConfigCMSG.Types.Job)Orders.Job,
        Courage = (global::Bnet.SetBestiaAiConfigCMSG.Types.Courage)Orders.Courage,
        Overrides = OrderSheet.OverridesToProto(following ? OwnOverrides : Orders.Overrides),
      };

      if (following)
      {
        msg.PresetId = PresetId;
      }
      else
      {
        msg.FallbackJob = (global::Bnet.SetBestiaAiConfigCMSG.Types.Job)Orders.FallbackJob;
        msg.Rules.AddRange(Orders.RulesToProto());
        if (Orders.AreaPlaceId != 0) msg.AreaPlaceId = Orders.AreaPlaceId;
        if (Orders.ChestPlaceId != 0) msg.ChestPlaceId = Orders.ChestPlaceId;
      }

      return new Envelope { SetBestiaAiConfig = msg };
    }
  }
}
