using Godot;

namespace BestiaBehemothClient.Bnet.Message.Map
{
  /// <summary>
  /// What the sky is doing where this player is standing.
  /// </summary>
  /// <remarks>
  /// Arrives on login, on crossing into different weather, when a channel moves past a hysteresis band, and on a
  /// two-minute heartbeat so a dropped message resyncs rather than leaving the client believing in an old sky.
  ///
  /// <para>
  /// <see cref="RegionId"/> is an <b>opaque change token</b>. Compare it for equality to know that the player has
  /// walked into different weather and a crossfade is wanted rather than a continuation; do not attach any meaning
  /// to the value and do not expect it to be stable across worlds. The server does not send the region geometry,
  /// deliberately: those boundaries are a map of where the weather changes, which is what the WEATHER_SENSE skill
  /// charges a player for.
  /// </para>
  /// </remarks>
  [GlobalClass]
  public partial class WeatherSMSG : MapSMSG
  {
    public enum Kind
    {
      Clear = 0,
      Cloudy = 1,
      Fog = 2,
      Rain = 3,
      HeavyRain = 4,
      Thunderstorm = 5,
      Snow = 6,
      Blizzard = 7,
      Sandstorm = 8,
      ManaStorm = 9,
      Tornado = 10
    }

    [Export] public int RegionId { get; set; }

    [Export] public Kind Weather { get; set; } = Kind.Clear;

    /// <summary>How hard, 0 to 1. Zero when the sky is merely clear or overcast.</summary>
    [Export] public float Intensity { get; set; }

    /// <summary>Cloud cover, 0 to 1. Separate from intensity: a dry sky can still be grey.</summary>
    [Export] public float CloudCover { get; set; }

    [Export] public float WindSpeed { get; set; }

    /// <summary>Radians, 0 pointing east, counter-clockwise. Weather arrives from the opposite bearing.</summary>
    [Export] public float WindDirection { get; set; }

    [Export] public float TemperatureCelsius { get; set; }

    /// <summary>Wind chill when cold, humidity when hot. What gameplay tolerance keys on.</summary>
    [Export] public float FeltTemperatureCelsius { get; set; }

    /// <summary>
    /// The next weather WEATHER_SENSE saw coming, and how many real seconds until it arrives.
    /// </summary>
    /// <remarks>
    /// The actual answer rather than a probability: the server's weather field is a pure function of
    /// (seed, region, time) with no state in it, so a forecast is the same evaluation at a later time.
    /// </remarks>
    [Export] public bool HasForecast { get; set; }
    [Export] public Kind ForecastWeather { get; set; } = Kind.Clear;
    [Export] public int ForecastInSeconds { get; set; }

    /// <summary>
    /// Where the tornado is, when <see cref="Weather"/> is <see cref="Kind.Tornado"/>.
    /// </summary>
    /// <remarks>
    /// Present because a weather region is sixteen kilometres across, so "there is a tornado nearby" is not
    /// something a player can act on. A point is.
    /// </remarks>
    [Export] public bool HasHazard { get; set; }
    [Export] public long HazardX { get; set; }
    [Export] public long HazardY { get; set; }
    [Export] public float HazardRadiusMetres { get; set; }

    public static WeatherSMSG FromProto(Bnet.WeatherSMSG proto)
    {
      return new WeatherSMSG
      {
        RegionId = (int)proto.RegionId,
        Weather = (Kind)proto.Kind,
        Intensity = proto.Intensity,
        CloudCover = proto.CloudCover,
        WindSpeed = proto.WindSpeed,
        WindDirection = proto.WindDirection,
        TemperatureCelsius = proto.TemperatureCelsius,
        FeltTemperatureCelsius = proto.FeltTemperatureCelsius,
        HasForecast = proto.HasForecast,
        ForecastWeather = (Kind)proto.ForecastKind,
        ForecastInSeconds = (int)proto.ForecastInSeconds,
        HasHazard = proto.HasHazard,
        HazardX = proto.HazardX,
        HazardY = proto.HazardY,
        HazardRadiusMetres = proto.HazardRadiusMetres
      };
    }

    public override string ToString()
    {
      var hazard = HasHazard ? $", tornado at ({HazardX},{HazardY}) r={HazardRadiusMetres:F0}m" : "";
      var forecast = HasForecast ? $", sensing {ForecastWeather} in {ForecastInSeconds}s" : "";
      return $"{Weather} intensity {Intensity:F2}, cloud {CloudCover:F2}, wind {WindSpeed:F1} m/s, " +
             $"{TemperatureCelsius:F1}°C (feels {FeltTemperatureCelsius:F1}°C), region {RegionId}" +
             $"{hazard}{forecast}";
    }
  }
}
