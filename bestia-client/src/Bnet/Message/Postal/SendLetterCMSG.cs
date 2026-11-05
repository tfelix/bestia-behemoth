using Bnet;

namespace BestiaBehemothClient.Bnet.Message.Postal
{
  /// <summary>Sends a letter by pigeon to the master with this name.</summary>
  public partial class SendLetterCMSG : ICMSG
  {
    public string RecipientName { get; set; } = "";
    public string Subject { get; set; } = "";
    public string Body { get; set; } = "";

    public override Envelope ToEnvelope()
    {
      return new Envelope
      {
        SendLetter = new global::Bnet.SendLetterCMSG
        {
          RecipientName = RecipientName,
          Subject = Subject,
          Body = Body
        }
      };
    }
  }
}
