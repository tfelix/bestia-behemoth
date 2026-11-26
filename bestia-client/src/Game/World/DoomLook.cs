using Godot;

namespace BestiaBehemothClient.Game.World
{
  /// <summary>How far the doomsday's own mood grades the sky at each of the server's levels, 0 to 4.</summary>
  /// <remarks>
  /// Stronger than <see cref="ManaLook"/> at every step but the first: where the doomsday is felt, it is the thing
  /// the sky is about.
  /// </remarks>
  public static class DoomLook
  {
    /// <summary>At the doomsday's heart.</summary>
    public const int HighestLevel = 4;

    //                                        none  faint  near  strong heart
    private static readonly float[] Moods = { 0.0f, 0.18f, 0.38f, 0.62f, 0.88f };

    public static float MoodStrength(int level)
    {
      return Moods[Mathf.Clamp(level, 0, HighestLevel)];
    }
  }
}
