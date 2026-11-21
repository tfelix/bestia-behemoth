using Godot;

namespace BestiaBehemothClient.Game.UI.Chat
{
  /// <summary>Hands <see cref="WhisperAddress"/> to GDScript, which cannot call a static C# method.</summary>
  [GlobalClass]
  public partial class WhisperAddressReader : RefCounted
  {
    /// <summary>The name, then the text.</summary>
    public string[] Split(string afterPrefix)
    {
      var (target, text) = WhisperAddress.Split(afterPrefix);

      return [target, text];
    }
  }
}
