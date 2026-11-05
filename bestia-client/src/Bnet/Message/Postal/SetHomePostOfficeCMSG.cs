using Bnet;

namespace BestiaBehemothClient.Bnet.Message.Postal
{
  /// <summary>Makes the office of this postmaster the master's home post office.</summary>
  public partial class SetHomePostOfficeCMSG : ICMSG
  {
    public ulong PostmasterEntityId { get; set; }

    public override Envelope ToEnvelope()
    {
      return new Envelope
      {
        SetHomePostOffice = new global::Bnet.SetHomePostOfficeCMSG { PostmasterEntityId = PostmasterEntityId }
      };
    }
  }
}
