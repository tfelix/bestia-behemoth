using Bnet;

namespace BestiaBehemothClient.Bnet.Message.Postal
{
  /// <summary>Throws a letter away.</summary>
  public partial class DeleteLetterCMSG : ICMSG
  {
    public ulong LetterId { get; set; }

    public override Envelope ToEnvelope()
    {
      return new Envelope { DeleteLetter = new global::Bnet.DeleteLetterCMSG { LetterId = LetterId } };
    }
  }
}
