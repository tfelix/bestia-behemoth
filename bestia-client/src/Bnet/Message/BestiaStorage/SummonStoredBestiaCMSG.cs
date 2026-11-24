using Bnet;
using Godot;

namespace BestiaBehemothClient.Bnet.Message.BestiaStorage
{
  /// <summary>Brings a stored bestia into a free active slot, next to its master.</summary>
  public partial class SummonStoredBestiaCMSG : ICMSG
  {
    [Export] public ulong PlayerBestiaId { get; set; }

    public override Envelope ToEnvelope()
    {
      return new Envelope
      {
        SummonStoredBestia = new global::Bnet.SummonStoredBestiaCMSG { PlayerBestiaId = PlayerBestiaId }
      };
    }
  }
}
