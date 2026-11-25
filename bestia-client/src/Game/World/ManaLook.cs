using Godot;

namespace BestiaBehemothClient.Game.World
{
  /// <summary>
  /// What a step of the mana ladder looks and sounds like: how far the sky turns violet, how thick the light
  /// motes drift and how loud the hum is.
  /// </summary>
  /// <remarks>
  /// A plain value type for <see cref="WeatherLook"/>'s reason: <c>ManaLookTest</c> pins it without a display.
  /// <see cref="WeatherState"/> eases toward it. Each value only ever grows with the tier, so a player who walks
  /// toward the mana feels it build.
  /// </remarks>
  public readonly struct ManaLook
  {
    /// <summary>The highest tier this build knows, Burn. A newer server may send more.</summary>
    public const int HighestTier = 7;

    //                                        Calm  Stirring Uneasy Ominous Storms Event Sickness Burn
    private static readonly float[] Moods = { 0.0f, 0.06f, 0.14f, 0.24f, 0.32f, 0.42f, 0.52f, 0.65f };
    private static readonly float[] Motes = { 0.0f, 0.2f, 0.4f, 0.6f, 0.75f, 0.9f, 1.0f, 1.0f };
    private static readonly float[] Hums = { 0.0f, 0.15f, 0.3f, 0.45f, 0.55f, 0.7f, 0.85f, 1.0f };

    private ManaLook(float moodStrength, float moteRate, float hum)
    {
      MoodStrength = moodStrength;
      MoteRate = moteRate;
      Hum = hum;
    }

    /// <summary>How much of the mana mood grades the sky, 0 to 1.</summary>
    public float MoodStrength { get; }

    /// <summary>How thick the drifting light motes are, 0 to 1. Mostly seen at night.</summary>
    public float MoteRate { get; }

    /// <summary>How loud the low hum is, 0 to 1.</summary>
    public float Hum { get; }

    /// <summary>The look of <paramref name="tier"/>. A tier above Burn reads as Burn, below Calm as Calm.</summary>
    public static ManaLook For(int tier)
    {
      var step = Mathf.Clamp(tier, 0, HighestTier);

      return new ManaLook(Moods[step], Motes[step], Hums[step]);
    }
  }
}
