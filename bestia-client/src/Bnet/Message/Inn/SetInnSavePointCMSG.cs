using Bnet;
using Godot;

namespace BestiaBehemothClient.Bnet.Message.Inn
{
  /// <summary>Makes the inn at this counter the master's save point.</summary>
  public partial class SetInnSavePointCMSG : ICMSG
  {
    [Export] public ulong CounterEntityId { get; set; }

    public override Envelope ToEnvelope()
    {
      return new Envelope
      {
        SetInnSavePoint = new global::Bnet.SetInnSavePointCMSG { CounterEntityId = CounterEntityId }
      };
    }
  }
}
