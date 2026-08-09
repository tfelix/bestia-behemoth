using BestiaBehemothClient.Bnet;
using BestiaBehemothClient.Bnet.Message;
using BestiaBehemothClient.Bnet.Message.Map;
using Godot;

namespace BestiaBehemothClient.Game.World
{
  /// <summary>
  /// Turns the server's weather into the handful of numbers the shaders actually want.
  /// </summary>
  /// <remarks>
  /// <c>WeatherSMSG</c> has been arriving and being decoded since the weather system was written, and nothing
  /// has ever read it. It carries a kind, an intensity and a temperature; what a shader needs is how wet the
  /// ground is and how much snow is lying, which are not the same thing - they are what the weather has *done*
  /// rather than what it is doing.
  ///
  /// <para>
  /// <b>That difference is the whole reason this class exists rather than a line in a shader.</b> Ground does
  /// not dry the instant rain stops, and snow does not vanish when it stops falling, so both are integrated
  /// over time and with different rates in each direction. A shader reading intensity directly would have
  /// puddles appear and disappear with the cloud cover.
  /// </para>
  ///
  /// <para>
  /// The values go to global shader parameters rather than to the terrain material, so that water, foliage and
  /// anything else added later reads the same numbers instead of keeping its own copy - and so they can be
  /// driven by hand from Project Settings while there is no server to ask.
  /// </para>
  ///
  /// <para>
  /// Attached the same way <c>ChunkStreamManager</c> is, and for the same reason: it has to outlive the Game
  /// scene, because weather arrives whenever the server feels like sending it.
  /// </para>
  /// </remarks>
  [GlobalClass]
  public partial class WeatherState : Node
  {
    private static readonly StringName Wetness = "weather_wetness";
    private static readonly StringName Snow = "weather_snow";
    private static readonly StringName Temperature = "weather_temperature";

    /// <summary>
    /// How long the ground takes to respond, in seconds, getting wetter and then drying again.
    /// </summary>
    /// <remarks>
    /// Asymmetric on purpose, and by a lot. Rain soaks in over about the time it takes to notice it is raining;
    /// what is left afterwards is a slick that takes minutes to go, which is the part that makes a shower feel
    /// like it happened rather than like a light switch.
    /// </remarks>
    [Export] public float WettingSeconds { get; set; } = 20.0f;

    [Export] public float DryingSeconds { get; set; } = 180.0f;

    /// <summary>Snow settles about as fast as rain wets, and lingers far longer once it has.</summary>
    [Export] public float SnowfallSeconds { get; set; } = 45.0f;

    [Export] public float ThawSeconds { get; set; } = 300.0f;

    /// <summary>Air temperature follows the region rather than integrating, so it only needs smoothing.</summary>
    [Export] public float TemperatureSeconds { get; set; } = 10.0f;

    private float _wetness;
    private float _snow;
    private float _temperature = 15.0f;

    private float _wetnessTarget;
    private float _snowTarget;
    private float _temperatureTarget = 15.0f;

    private bool _attached;

    public void Attach(BnetSocket socket)
    {
      if (socket == null)
      {
        GD.PushWarning("WeatherState has no BnetSocket; weather will stay at its defaults.");
        return;
      }

      socket.MessageReceived += OnMessageReceived;
      _attached = true;

      Publish();
    }

    public override void _ExitTree()
    {
      if (_attached)
      {
        // Nothing else holds this, and the socket outlives the scene.
        _attached = false;
      }
    }

    private void OnMessageReceived(ISMSG message)
    {
      if (message is not WeatherSMSG weather)
      {
        return;
      }

      _temperatureTarget = weather.TemperatureCelsius;

      var intensity = Mathf.Clamp(weather.Intensity, 0.0f, 1.0f);

      // Snow and rain are the same sky doing one thing or the other, so a kind contributes to exactly one.
      // Fog wets the ground a little without falling, which is most of what makes a foggy morning look like one.
      _snowTarget = weather.Weather switch
      {
        WeatherSMSG.Kind.Snow => intensity,
        WeatherSMSG.Kind.Blizzard => Mathf.Max(intensity, 0.6f),
        _ => 0.0f
      };

      _wetnessTarget = weather.Weather switch
      {
        WeatherSMSG.Kind.Rain => intensity,
        WeatherSMSG.Kind.HeavyRain => Mathf.Max(intensity, 0.7f),
        WeatherSMSG.Kind.Thunderstorm => Mathf.Max(intensity, 0.8f),
        WeatherSMSG.Kind.Fog => intensity * 0.35f,
        _ => 0.0f
      };
    }

    public override void _Process(double delta)
    {
      var step = (float)delta;

      var wetness = Approach(_wetness, _wetnessTarget, step, WettingSeconds, DryingSeconds);
      var snow = Approach(_snow, _snowTarget, step, SnowfallSeconds, ThawSeconds);
      var temperature = Approach(_temperature, _temperatureTarget, step, TemperatureSeconds, TemperatureSeconds);

      // Only publish on a change worth a round trip to the rendering server - the values are stable for minutes
      // at a time and this runs every frame.
      if (Mathf.IsEqualApprox(wetness, _wetness) &&
          Mathf.IsEqualApprox(snow, _snow) &&
          Mathf.IsEqualApprox(temperature, _temperature))
      {
        return;
      }

      _wetness = wetness;
      _snow = snow;
      _temperature = temperature;

      Publish();
    }

    /// <summary>
    /// Exponential approach with a different time constant in each direction.
    /// </summary>
    /// <remarks>
    /// Framerate independent, which a plain lerp by <c>delta</c> is not - and it matters here because the slow
    /// direction is measured in minutes, where the error would compound into a visibly different world on a
    /// fast machine than on a slow one.
    /// </remarks>
    private static float Approach(float value, float target, float delta, float riseSeconds, float fallSeconds)
    {
      var seconds = Mathf.Max(target > value ? riseSeconds : fallSeconds, 0.001f);

      return Mathf.Lerp(value, target, 1.0f - Mathf.Exp(-delta / seconds));
    }

    private void Publish()
    {
      RenderingServer.GlobalShaderParameterSet(Wetness, _wetness);
      RenderingServer.GlobalShaderParameterSet(Snow, _snow);
      RenderingServer.GlobalShaderParameterSet(Temperature, _temperature);
    }
  }
}
