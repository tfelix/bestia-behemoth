using BestiaBehemothClient.Bnet.Message.Map;
using Godot;

namespace BestiaBehemothClient.Game.World
{
  /// <summary>
  /// What a weather reading should look like: the decisions, separated from the plumbing that smooths them.
  /// </summary>
  /// <remarks>
  /// A plain value type with no engine in it, for the reason <see cref="DayCycle"/> is one - a test cannot
  /// construct a <see cref="Node"/>, and the interesting part of weather rendering is not "did the message
  /// arrive" but "does every kind map to something". That is today's bug rather than a hypothetical one: the
  /// switch in <see cref="WeatherState"/> answers six of the eleven kinds and silently renders the other five
  /// as a clear sky, which at runtime is indistinguishable from the weather simply being clear.
  ///
  /// <para>
  /// <see cref="WeatherState"/> owns the smoothing and the node graph; this owns the numbers. The split is
  /// what lets <c>WeatherLookTest</c> pin the curves without a display.
  /// </para>
  ///
  /// <para>
  /// Everything here is the <i>target</i> for a reading. Nothing in this struct changes over time, so it is
  /// cheap enough to rebuild whenever a message lands.
  /// </para>
  /// </remarks>
  public readonly struct WeatherLook
  {
    /// <summary>The look of a sky nobody has said anything about yet.</summary>
    public static readonly WeatherLook Clear = For(WeatherSMSG.Kind.Clear, 0.0f, 0.0f);

    private WeatherLook(float rainRate, float snowRate, float dustRate, float groundWetRate, float overcast,
      float visibility, float windScale, bool hasLightning, Color hazeColour = default, float hazeTint = 0.0f)
    {
      HazeColour = hazeColour;
      HazeTint = hazeTint;
      RainRate = rainRate;
      SnowRate = snowRate;
      DustRate = dustRate;
      GroundWetRate = groundWetRate;
      Overcast = overcast;
      Visibility = visibility;
      WindScale = windScale;
      HasLightning = hasLightning;
    }

    /// <summary>How hard rain is falling right now, 0 to 1. Not how wet the ground is.</summary>
    /// <remarks>
    /// The distinction the whole struct hangs on. Ground wetness is what the rain has <i>done</i> and is
    /// measured in minutes; this is what the sky is doing and changes in seconds. Particles driven off the
    /// accumulated value would keep raining for three minutes after the cloud had gone.
    /// </remarks>
    public float RainRate { get; }

    /// <summary>How hard snow is falling right now, 0 to 1.</summary>
    public float SnowRate { get; }

    /// <summary>How thick the airborne dust is right now, 0 to 1. Sandstorms only.</summary>
    public float DustRate { get; }

    /// <summary>
    /// How fast the ground is being wetted, 0 to 1 - which is not always <see cref="RainRate"/>.
    /// </summary>
    /// <remarks>
    /// Here rather than derived by the caller so that this struct stays the only place that switches over the
    /// kind. Two switches over one enum in two files is exactly how five of the eleven kinds came to be
    /// handled in one and forgotten in the other.
    ///
    /// <para>
    /// Fog is the case that makes it worth a field of its own: it wets the ground without anything falling,
    /// and that damp is most of what makes a foggy morning look like one.
    /// </para>
    /// </remarks>
    public float GroundWetRate { get; }

    /// <summary>
    /// How grey the sky goes, 0 to 1: the cloud cover the <i>look</i> uses, which is not always the cover the
    /// server sent.
    /// </summary>
    /// <remarks>
    /// Floored per kind. A server reporting rain at forty percent cover is describing a shower under a broken
    /// sky, but rain falling out of a bright blue sky reads as the weather not having applied - so the
    /// precipitating kinds set a floor, and the reported cover can only push it higher.
    /// </remarks>
    public float Overcast { get; }

    /// <summary>How far one can see, 1 on a clear day down to near zero in a whiteout.</summary>
    public float Visibility { get; }

    /// <summary>
    /// A bias on the reported wind, for the kinds that are defined by their wind.
    /// </summary>
    /// <remarks>
    /// Deliberately small, and deliberately not a substitute for the real number - the server's wind already
    /// rises in a storm. This only stops a blizzard whose wind happens to be reported low from rendering as
    /// snow drifting gently straight down, which is the one thing a blizzard is not.
    /// </remarks>
    public float WindScale { get; }

    /// <summary>Whether this sky flashes.</summary>
    public bool HasLightning { get; }

    /// <summary>What colour the air itself goes, for the kinds whose air has a colour.</summary>
    /// <remarks>
    /// Only meaningful where <see cref="HazeTint"/> is above zero. Rain and snow put water in the air and
    /// water is colourless, so the fog they thicken stays the colour the sky lights it - which is what the
    /// overcast palette already does. Sand is not colourless, and a grey sandstorm reads as fog.
    /// </remarks>
    public Color HazeColour { get; }

    /// <summary>How strongly <see cref="HazeColour"/> takes over the fog at zero visibility, 0 to 1.</summary>
    public float HazeTint { get; }

    /// <summary>Whether anything is falling out of this sky.</summary>
    public bool IsPrecipitating
    {
      get { return RainRate > 0.0f || SnowRate > 0.0f || DustRate > 0.0f; }
    }

    /// <summary>
    /// How strongly cloud shadows read on the ground, 0 to 1, before the sun's own height is applied.
    /// </summary>
    /// <remarks>
    /// Rises to a peak around half cover and then <b>falls again</b>, which is the detail that makes the
    /// effect read as weather rather than as blotches. Distinct shadows need distinct clouds; a solid
    /// overcast has none, and what it produces is uniform gloom - which is <see cref="Overcast"/>'s job and
    /// not this one. The floor under a full sky is small rather than zero, because a perfectly even ground
    /// looks like a feature that failed to load.
    /// </remarks>
    public float ShadowStrength
    {
      get
      {
        var risen = Smoothstep(0.05f, 0.45f, Overcast);
        var closingOver = Smoothstep(0.7f, 1.0f, Overcast);

        return risen * (1.0f - closingOver * 0.85f);
      }
    }

    /// <summary>What to multiply the sun's authored energy by.</summary>
    public float SunEnergyScale
    {
      get { return Mathf.Lerp(1.0f, 0.32f, Overcast); }
    }

    /// <summary>
    /// How wide to open the sun's angular diameter, in degrees, so shadows soften as well as dim.
    /// </summary>
    /// <remarks>
    /// Cloud does not merely block light, it scatters it: an overcast sky is one enormous area light and its
    /// shadows have no edge to speak of. Dimming alone gives sharp black shadows in a grey world, which is
    /// what an eclipse looks like rather than what a dull day looks like.
    ///
    /// <para>
    /// <b>The ceiling is small, and measured rather than reasoned.</b> Physically an overcast sky is a
    /// hemisphere, so the honest number is enormous - but Godot spends a fixed filter budget across whatever
    /// penumbra this asks for, and past about four degrees a shadow stops being soft and simply stops. A
    /// sweep against a five-metre tree and a person-sized capsule put the boundary between three, where both
    /// still read, and five, where the person had already lost theirs entirely. So this caps below the point
    /// where softening turns into deleting, and the rest of the overcast look is carried by the dimming and
    /// by the grey sky the ambient is sampled from.
    /// </para>
    /// </remarks>
    public float SunAngularDegrees
    {
      get { return Mathf.Lerp(0.5f, 3.0f, Overcast); }
    }

    /// <summary>
    /// How much of the authored twilight colour survives, 0 to 1.
    /// </summary>
    /// <remarks>
    /// An overcast sunset is grey. The orange comes from a long clear path through the atmosphere, and cloud
    /// is exactly what removes it.
    /// </remarks>
    public float TwilightScale
    {
      get { return Mathf.Lerp(1.0f, 0.25f, Overcast); }
    }

    /// <summary>
    /// The look for one weather reading.
    /// </summary>
    /// <param name="kind">What the sky is doing.</param>
    /// <param name="intensity">How hard, 0 to 1. Zero for the kinds that are merely a sky.</param>
    /// <param name="cloudCover">Reported cover, 0 to 1. Separate from intensity: a dry sky can still be grey.</param>
    public static WeatherLook For(WeatherSMSG.Kind kind, float intensity, float cloudCover)
    {
      var hard = Mathf.Clamp(intensity, 0.0f, 1.0f);
      var cover = Mathf.Clamp(cloudCover, 0.0f, 1.0f);

      // Every kind gets an arm. The trailing default is unreachable for the enum as it stands and exists only
      // because a proto enum can carry a number this build has never heard of - appending is documented as
      // safe, so a newer server must degrade to a clear sky rather than throw.
      return kind switch
      {
        WeatherSMSG.Kind.Clear =>
          Dry(cover, 1.0f, 1.0f),

        WeatherSMSG.Kind.Cloudy =>
          Dry(Mathf.Max(cover, 0.55f), 1.0f, 1.0f),

        // The one dry sky that still wets the ground.
        WeatherSMSG.Kind.Fog =>
          Damp(hard * 0.35f, Mathf.Max(cover, 0.6f), Mathf.Lerp(0.45f, 0.07f, hard), 0.6f),

        WeatherSMSG.Kind.Rain =>
          Rainy(Mathf.Max(hard, 0.25f), Mathf.Max(cover, 0.75f), Mathf.Lerp(0.9f, 0.6f, hard), 1.0f, false),

        WeatherSMSG.Kind.HeavyRain =>
          Rainy(Mathf.Max(hard, 0.7f), Mathf.Max(cover, 0.88f), Mathf.Lerp(0.7f, 0.4f, hard), 1.15f, false),

        WeatherSMSG.Kind.Thunderstorm =>
          Rainy(Mathf.Max(hard, 0.8f), Mathf.Max(cover, 0.92f), Mathf.Lerp(0.6f, 0.35f, hard), 1.25f, true),

        WeatherSMSG.Kind.Snow =>
          Snowy(Mathf.Max(hard, 0.25f), Mathf.Max(cover, 0.7f), Mathf.Lerp(0.8f, 0.5f, hard), 1.0f),

        WeatherSMSG.Kind.Blizzard =>
          Snowy(Mathf.Max(hard, 0.75f), Mathf.Max(cover, 0.95f), Mathf.Lerp(0.35f, 0.08f, hard), 1.4f),

        WeatherSMSG.Kind.Sandstorm =>
          Dusty(Mathf.Max(hard, 0.6f), Mathf.Max(cover, 0.5f), Mathf.Lerp(0.4f, 0.1f, hard), 1.6f,
            new Color(0.78f, 0.64f, 0.42f), 0.85f),

        // Ignores shelter and can stand in a clear sky, so it takes the reported cover as it finds it rather
        // than flooring it. The motes it should have are not built; until they are, the honest rendering is a
        // charged, hazy sky with lightning in it, and not a downpour it does not have.
        WeatherSMSG.Kind.ManaStorm =>
          Dry(cover, Mathf.Lerp(0.85f, 0.55f, hard), 1.2f, true, new Color(0.55f, 0.44f, 0.86f), 0.6f),

        // No funnel yet - the hazard fields say where it is and nothing draws it. Rendered as the storm that
        // carries one, which is at least the right weather to be caught outdoors in.
        WeatherSMSG.Kind.Tornado =>
          Rainy(Mathf.Max(hard, 0.6f), Mathf.Max(cover, 0.95f), Mathf.Lerp(0.5f, 0.25f, hard), 1.8f, true),

        // Unreachable for the enum as it stands, and not removable: a proto enum can carry a number this
        // build has never heard of, and appending kinds is documented as safe. A newer server degrades to a
        // clear sky rather than throwing.
        _ => Dry(cover, 1.0f, 1.0f)
      };
    }

    /// <summary>A sky with nothing falling out of it and nothing settling under it.</summary>
    private static WeatherLook Dry(float overcast, float visibility, float windScale,
      bool hasLightning = false, Color hazeColour = default, float hazeTint = 0.0f)
    {
      return new WeatherLook(0.0f, 0.0f, 0.0f, 0.0f, overcast, visibility, windScale, hasLightning,
        hazeColour, hazeTint);
    }

    /// <summary>A sky with nothing falling out of it that wets the ground anyway.</summary>
    private static WeatherLook Damp(float groundWetRate, float overcast, float visibility, float windScale)
    {
      return new WeatherLook(0.0f, 0.0f, 0.0f, groundWetRate, overcast, visibility, windScale, false);
    }

    /// <summary>Rain wets the ground at the rate it falls, so the two are one number.</summary>
    private static WeatherLook Rainy(float rate, float overcast, float visibility, float windScale,
      bool hasLightning)
    {
      return new WeatherLook(rate, 0.0f, 0.0f, rate, overcast, visibility, windScale, hasLightning);
    }

    /// <summary>Snow lies rather than wets, so the ground stays dry however hard it comes down.</summary>
    private static WeatherLook Snowy(float rate, float overcast, float visibility, float windScale)
    {
      return new WeatherLook(0.0f, rate, 0.0f, 0.0f, overcast, visibility, windScale, false);
    }

    private static WeatherLook Dusty(float rate, float overcast, float visibility, float windScale,
      Color hazeColour, float hazeTint)
    {
      return new WeatherLook(0.0f, 0.0f, rate, 0.0f, overcast, visibility, windScale, false, hazeColour,
        hazeTint);
    }

    /// <summary>Hermite ease between two edges, clamped.</summary>
    private static float Smoothstep(float from, float to, float value)
    {
      var x = Mathf.Clamp((value - from) / (to - from), 0.0f, 1.0f);

      return x * x * (3.0f - 2.0f * x);
    }
  }
}
