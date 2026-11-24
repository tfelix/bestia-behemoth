using Bnet;

namespace BestiaBehemothClient.Bnet.Message.BestiaStorage
{
  /// <summary>Asks for the selected master's bestia storage; answered with a <see cref="BestiaStorageSMSG"/>.</summary>
  public partial class OpenBestiaStorageCMSG : ICMSG
  {
    public override Envelope ToEnvelope()
    {
      return new Envelope
      {
        OpenBestiaStorage = new global::Bnet.OpenBestiaStorageCMSG()
      };
    }
  }
}
