using Bnet;
using Godot;

namespace BestiaBehemothClient.Bnet.Message.Inn
{
  /// <summary>Pays the inn to restore every bestia out in the world, at no more than the price shown.</summary>
  public partial class TendBestiasCMSG : ICMSG
  {
    [Export] public ulong CounterEntityId { get; set; }
    [Export] public int ExpectedPrice { get; set; }

    public override Envelope ToEnvelope()
    {
      return new Envelope
      {
        TendBestias = new global::Bnet.TendBestiasCMSG { CounterEntityId = CounterEntityId, ExpectedPrice = (uint)ExpectedPrice }
      };
    }
  }
}
