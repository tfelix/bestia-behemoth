using Godot;

namespace BestiaBehemothClient.Game.World
{
  /// <summary>
  /// The seconds fire records are stamped in, published to every shader as <c>ground_fire_clock</c>.
  /// </summary>
  /// <remarks>
  /// Not the shaders' <c>TIME</c>: that one rolls over every hour, and a record stamped before the rollover
  /// would read as far in the future.
  /// </remarks>
  public static class GroundFireClock
  {
    /// <summary>The time that is never reached, so a zero texel reads as "no fire here".</summary>
    public const float Never = 0f;

    private static readonly StringName Parameter = "ground_fire_clock";

    /// <summary>Seconds since the client started, offset so the very first frame is already past <see cref="Never"/>.</summary>
    public static float Now => (float)(Time.GetTicksUsec() / 1_000_000.0) + 1f;

    public static void Publish() => RenderingServer.GlobalShaderParameterSet(Parameter, Now);
  }
}
