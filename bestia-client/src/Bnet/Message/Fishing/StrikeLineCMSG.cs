using Bnet;

namespace BestiaBehemothClient.Bnet.Message.Fishing
{
  /// <summary>Pulls the line in: a catch while a fish bites, nothing before.</summary>
  public partial class StrikeLineCMSG : ICMSG
  {
    public override Envelope ToEnvelope()
    {
      return new Envelope
      {
        StrikeLine = new global::Bnet.StrikeLineCMSG()
      };
    }
  }
}
