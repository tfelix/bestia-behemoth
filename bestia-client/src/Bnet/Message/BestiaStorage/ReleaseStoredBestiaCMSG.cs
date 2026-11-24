using Bnet;
using Godot;

namespace BestiaBehemothClient.Bnet.Message.BestiaStorage
{
  /// <summary>Gives up a stored bestia for good.</summary>
  public partial class ReleaseStoredBestiaCMSG : ICMSG
  {
    [Export] public ulong PlayerBestiaId { get; set; }

    public override Envelope ToEnvelope()
    {
      return new Envelope
      {
        ReleaseStoredBestia = new global::Bnet.ReleaseStoredBestiaCMSG { PlayerBestiaId = PlayerBestiaId }
      };
    }
  }
}
