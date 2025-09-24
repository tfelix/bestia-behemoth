using Bnet;

namespace BestiaBehemothClient.Bnet.Message
{
  public partial class Authentication(
      string token,
      string clientVersion
      ) : ICMSG
  {

    // Remaining implementation of Person class.
    public string Token { get; } = token;
    public string ClientVersion { get; } = clientVersion;

    public override Envelope ToEnvelope()
    {
      var authentication = new global::Bnet.Authentication
      {
        Token = Token,
        ClientVersion = ClientVersion
      };

      return new Envelope
      {
        Authentication = authentication
      };
    }
  }
}