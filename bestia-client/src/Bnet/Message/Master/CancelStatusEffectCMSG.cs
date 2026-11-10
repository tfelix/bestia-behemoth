using Bnet;
using Godot;

namespace BestiaBehemothClient.Bnet.Message.Master
{
  /// <summary>Drops a buff from the controlled entity; the server ignores anything not the bearer's to end.</summary>
  public partial class CancelStatusEffectCMSG : ICMSG
  {
    [Export] public ulong EffectId { get; set; }

    public override Envelope ToEnvelope()
    {
      return new Envelope
      {
        CancelStatusEffect = new global::Bnet.CancelStatusEffectCMSG { EffectId = EffectId }
      };
    }
  }
}
