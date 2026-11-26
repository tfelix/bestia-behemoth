using System;

namespace BestiaBehemothClient.Auth
{
  /// <summary>
  /// The login server will not start the game because the world ended and the next one is still being born. The
  /// stored session stays good: the client waits and tries again.
  /// </summary>
  public sealed class WorldRebirthException : Exception
  {
    public WorldRebirthException() : base("A new world is being born")
    {
    }
  }
}
