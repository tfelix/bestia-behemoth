using Bnet;

namespace BestiaBehemothClient.Bnet.Message.Postal
{
  /// <summary>Marks a letter as read.</summary>
  public partial class ReadLetterCMSG : ICMSG
  {
    public ulong LetterId { get; set; }

    public override Envelope ToEnvelope()
    {
      return new Envelope { ReadLetter = new global::Bnet.ReadLetterCMSG { LetterId = LetterId } };
    }
  }
}
