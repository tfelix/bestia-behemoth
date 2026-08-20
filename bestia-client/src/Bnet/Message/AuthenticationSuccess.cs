using Godot;

namespace BestiaBehemothClient.Bnet.Message
{
  [GlobalClass]
  public partial class AuthenticationSuccess : ISMSG
  {
    /// <summary>
    /// Credential for this client's HTTP requests, good for exactly as long as this connection. Empty from a
    /// server too old to send one, which the map treats as "no map this session" rather than guessing.
    /// </summary>
    [Export]
    public string HttpTicket { get; set; } = string.Empty;

    public static AuthenticationSuccess FromProto(global::Bnet.AuthenticationSuccess proto)
    {
      return new AuthenticationSuccess
      {
        HttpTicket = proto.HttpTicket
      };
    }
  }
}
