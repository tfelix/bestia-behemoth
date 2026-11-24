using Bnet;
using Godot;

namespace BestiaBehemothClient.Bnet.Message.BestiaStorage
{
  /// <summary>Puts an active bestia into storage and the stored one where the active one stood.</summary>
  public partial class SwapStoredBestiaCMSG : ICMSG
  {
    [Export] public ulong StoredPlayerBestiaId { get; set; }
    [Export] public ulong ActiveEntityId { get; set; }

    public override Envelope ToEnvelope()
    {
      return new Envelope
      {
        SwapStoredBestia = new global::Bnet.SwapStoredBestiaCMSG
        {
          StoredPlayerBestiaId = StoredPlayerBestiaId,
          ActiveEntityId = ActiveEntityId
        }
      };
    }
  }
}
