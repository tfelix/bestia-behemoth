using Bnet;

namespace BestiaBehemothClient.Bnet.Message.Master
{
  public partial class GetSelfCMSG : ICMSG
  {

    public override Envelope ToEnvelope()
    {
      var getSelf = new global::Bnet.GetSelfCMSG();

      return new Envelope
      {
        GetSelf = getSelf
      };
    }
  }
}