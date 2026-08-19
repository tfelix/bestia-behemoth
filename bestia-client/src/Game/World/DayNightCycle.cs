using Godot;

namespace BestiaBehemothClient.Game.World
{
  /// <summary>
  /// Drives the sun, the moon, the sky and the fog from the world clock.
  /// </summary>
  /// <remarks>
  /// Everything here is one scalar's worth of work: <see cref="WorldClock.Daylight"/> is how much of the
  /// sun's light is up, and every colour and energy below is a blend keyed off it. The clock owns *when*,
  /// which is the server's business, and this owns *what it looks like*, which is not.
  ///
  /// <para>
  /// <b>Read per frame rather than driven by a signal.</b> <c>WorldClock.TimeChanged</c> fires once an
  /// in-game minute - about every twenty real seconds - which is right for a HUD readout and useless for a
  /// sunset. There is no message behind this either: the clock is an anchor sent once per connection, so the
  /// ramp is evaluated locally off boundaries the server stated.
  /// </para>
  ///
  /// <para>
  /// <b>The scene's authored lighting is left alone until the clock is anchored</b>, so the Game scene opens
  /// in the editor looking the way it was built rather than pitch black, and a login that has not yet had its
  /// world info does not flash a night at the player.
  /// </para>
  ///
  /// <para>
  /// Created and wired by <c>game.gd</c> rather than placed in Game.tscn, the way the terrain and prop
  /// renderers are: a scene node needs a resource uid that only the Godot editor can mint. It does not create
  /// its own lights, though - <see cref="Configure"/> takes the ones the scene authored, so their shadow
  /// settings, cull masks and angular sizes stay editable where a level designer would look for them.
  /// </para>
  /// </remarks>
  [GlobalClass]
  public partial class DayNightCycle : Node
  {
    /// <summary>
    /// How high the sun climbs at its peak, in degrees.
    /// </summary>
    /// <remarks>
    /// Not 90. A sun directly overhead flattens every slope in the world at midday, which costs exactly the
    /// relief the terrain's normal maps were added to show.
    /// </remarks>
    [Export(PropertyHint.Range, "10,89,1")] public float PeakElevationDegrees { get; set; } = 68.0f;

    /// <summary>
    /// How close to the horizon either light is allowed to get, in degrees. The arc flattens out here; the
    /// colour and the energy carry the rest of the sunset.
    /// </summary>
    /// <remarks>
    /// A shadow is <c>height / tan(elevation)</c> long, so its far end runs away as <c>1/elevation²</c> while
    /// the sun itself is still turning at a placid hundredth of a degree per second. Twelve real minutes
    /// before sunset a ten-metre tree already throws a hundred-metre shadow whose tip is crossing the ground
    /// at a quarter of a metre per second, and six minutes later that tip is doing walking pace - which reads
    /// on screen as the shadow's far edge sliding and smearing rather than as the sun going down. The same
    /// geometry stretches every shadow-map texel by <c>1/sin(elevation)</c>, so the edge going soft and the
    /// edge going loose arrive together.
    ///
    /// <para>
    /// Nine degrees keeps a ten-metre caster inside sixty-odd metres of shadow, which is comfortably within
    /// the range the directional light rasterises. Raising this trades sunset length for edge stability; the
    /// arc is worth watching at 1 to see what is being bought.
    /// </para>
    ///
    /// <para>
    /// This is also what keeps the sun from lighting the world from underneath. <see cref="ApplyLights"/>
    /// hides it on brightness, and brightness lags the geometry badly: the sun is still at half energy the
    /// moment it crosses the horizon, and does not fade out until the raw arc has taken it two dozen degrees
    /// below it - a long stretch of a shadow-casting light shining up through the ground.
    /// </para>
    /// </remarks>
    [Export(PropertyHint.Range, "1,30,1")] public float MinElevationDegrees { get; set; } = 9.0f;

    /// <summary>
    /// How far the sun's bearing swings between rising and setting, in degrees, centred on
    /// <see cref="BaseAzimuthDegrees"/>.
    /// </summary>
    /// <remarks>
    /// Well short of the 180 a real sun sweeps. Shadows that rotate through a half circle over a session
    /// read as the world spinning rather than as the day passing, because the camera is fixed relative to
    /// the ground and there is nothing on screen to attribute the motion to.
    /// </remarks>
    [Export(PropertyHint.Range, "0,180,1")] public float SweepDegrees { get; set; } = 120.0f;

    /// <summary>The bearing the sun crosses at its peak. Shadows point away from this at midday.</summary>
    [Export(PropertyHint.Range, "-180,180,1")] public float BaseAzimuthDegrees { get; set; } = 40.0f;

    [Export] public float SunEnergy { get; set; } = 1.0f;

    /// <summary>
    /// How bright the moon is. Small, but never zero.
    /// </summary>
    /// <remarks>
    /// A night lit only by ambient sky is flat - no shape on the terrain and no direction to anything. This
    /// is what keeps a hillside at midnight readable as a hillside.
    /// </remarks>
    [Export] public float MoonEnergy { get; set; } = 0.16f;

    [Export] public Color SunDayColour { get; set; } = new(1.0f, 0.96f, 0.88f);

    /// <summary>What the sun goes as it nears the horizon, and what dawn and dusk take their cast from.</summary>
    [Export] public Color SunTwilightColour { get; set; } = new(1.0f, 0.52f, 0.26f);

    [Export] public Color MoonColour { get; set; } = new(0.55f, 0.67f, 1.0f);

    [Export] public Color SkyTopDay { get; set; } = new(0.845f, 0.906f, 0.989f);
    [Export] public Color SkyTopTwilight { get; set; } = new(0.24f, 0.24f, 0.46f);
    [Export] public Color SkyTopNight { get; set; } = new(0.021f, 0.030f, 0.086f);

    [Export] public Color SkyHorizonDay { get; set; } = new(0.646f, 0.656f, 0.671f);
    [Export] public Color SkyHorizonTwilight { get; set; } = new(0.86f, 0.42f, 0.24f);
    [Export] public Color SkyHorizonNight { get; set; } = new(0.055f, 0.075f, 0.15f);

    [Export] public Color GroundDay { get; set; } = new(0.646f, 0.656f, 0.671f);
    [Export] public Color GroundTwilight { get; set; } = new(0.33f, 0.22f, 0.20f);
    [Export] public Color GroundNight { get; set; } = new(0.026f, 0.030f, 0.048f);

    [Export] public Color FogDay { get; set; } = new(0.630f, 0.729f, 0.829f);
    [Export] public Color FogTwilight { get; set; } = new(0.72f, 0.42f, 0.30f);
    [Export] public Color FogNight { get; set; } = new(0.045f, 0.060f, 0.115f);

    /// <summary>
    /// How much of the twilight palette the crossover actually gets, in <c>[0, 1]</c>.
    /// </summary>
    /// <remarks>
    /// A full-strength orange at every sunrise and sunset is a postcard the first time and a nuisance by the
    /// third, and the player will see three in a session. Turn it up to check the palette; leave it here to
    /// play under it.
    /// </remarks>
    [Export(PropertyHint.Range, "0,1,0.01")] public float TwilightStrength { get; set; } = 0.75f;

    private WorldClock _clock;
    private DirectionalLight3D _sun;
    private DirectionalLight3D _moon;
    private Environment _environment;
    private ProceduralSkyMaterial _sky;

    /// <summary>
    /// Hands over the scene's lighting and the clock to drive it from.
    /// </summary>
    /// <remarks>
    /// Everything is optional and a missing piece is warned about once rather than per frame: this is a
    /// visual layer, and a client that cannot find its sky should still be playable in whatever light the
    /// scene was authored with.
    /// </remarks>
    public void Configure(WorldEnvironment worldEnvironment, DirectionalLight3D sun, DirectionalLight3D moon,
      WorldClock clock)
    {
      _sun = sun;
      _moon = moon;
      _clock = clock;

      if (clock == null)
      {
        GD.PushWarning("[daynight] no WorldClock; the scene's authored lighting will not change.");
      }

      _environment = worldEnvironment?.Environment;

      // The sky material is reached through the Environment rather than exported separately, because the two
      // have to be the *same* sky - the one the background draws and the one ambient light is sampled from.
      _sky = _environment?.Sky?.SkyMaterial as ProceduralSkyMaterial;

      if (_sky == null)
      {
        GD.PushWarning(
          "[daynight] the environment has no ProceduralSkyMaterial; the sky will not follow the clock. " +
          "Lights and fog still will.");
      }

      if (_moon != null)
      {
        // Never casts shadows, and that is a decision rather than an oversight: a second shadow-casting
        // directional light doubles the shadow pass for a fill that is a sixth of the sun's brightness. The
        // sun is hidden outright at night, so nothing is paying for a shadow map nobody can see.
        _moon.ShadowEnabled = false;
        _moon.LightColor = MoonColour;
      }
    }

    public override void _Process(double delta)
    {
      if (_clock == null || !_clock.IsAnchored())
      {
        return;
      }

      var daylight = (float)_clock.Daylight;

      // Peaks at 1 exactly where daylight crosses a half - which is the middle of each ramp, and so the
      // moment the sun is on the horizon. One scalar gives both the blend and the sun's own warm cast.
      var twilight = (1.0f - Mathf.Abs(2.0f * daylight - 1.0f)) * TwilightStrength;

      ApplyLights(daylight, twilight);
      ApplySky(daylight, twilight);
    }

    private void ApplyLights(float daylight, float twilight)
    {
      var progress = SolarProgress((float)_clock.HourOfDay);

      // The arc the sun would follow if the horizon were not in the way, which both lights are placed off:
      // the moon is this turned around, so deriving it from the raw angle rather than from the sun's own
      // floored rotation is what lets the moon rise while the sun is pinned above the skyline.
      //
      // The arc is pinned to the twilight ramps rather than to the full-day band: the sun crosses zero at the
      // *midpoint* of each ramp, so sunrise happens when the light is half up. That is what makes the orange
      // land at the same moment the sun is on the skyline instead of an hour off it.
      //
      // Elevation straight from the light level would hold the sun at its peak for the whole fourteen-hour
      // day, and a sun that does not move casts shadows that do not move. This is its own curve.
      var elevation = Mathf.DegToRad(PeakElevationDegrees) * Mathf.Sin(Mathf.Pi * progress);
      var azimuth = Mathf.DegToRad(BaseAzimuthDegrees + (progress - 0.5f) * SweepDegrees);

      if (_sun != null)
      {
        _sun.Rotation = LightRotation(elevation, azimuth);
        _sun.LightEnergy = SunEnergy * daylight;
        _sun.LightColor = SunDayColour.Lerp(SunTwilightColour, twilight);

        // Hidden rather than merely dark. A DirectionalLight3D at zero energy still renders its shadow map,
        // and the whole of full night would be paying for a pass that contributes nothing.
        _sun.Visible = daylight > 0.002f;
      }

      if (_moon != null)
      {
        // The anti-sun: it rises as the sun sets, which is free and is also roughly what a moon does.
        _moon.Rotation = LightRotation(-elevation, azimuth + Mathf.Pi);
        _moon.LightEnergy = MoonEnergy * (1.0f - daylight);
        _moon.Visible = daylight < 0.998f;
      }
    }

    /// <summary>
    /// A bearing and an elevation as an Euler rotation for a light pointing along its own -Z, with the
    /// elevation held at or above <see cref="MinElevationDegrees"/>.
    /// </summary>
    /// <remarks>
    /// The floor is applied here rather than to the arc so that the arc stays the honest astronomy and this
    /// stays the one place either light is placed. A light whose turn is past the floor simply stops
    /// descending; nothing else about the hour it represents changes.
    /// </remarks>
    private Vector3 LightRotation(float elevation, float azimuth) =>
      new(-Mathf.Max(elevation, Mathf.DegToRad(MinElevationDegrees)), azimuth, 0.0f);

    /// <summary>
    /// How far through its arc the sun is: <c>0</c> at sunrise, <c>0.5</c> at solar noon, <c>1</c> at sunset,
    /// and on to <c>2</c> at the next sunrise.
    /// </summary>
    /// <remarks>
    /// Two arcs and not one, which is the whole subtlety here. The day is fourteen hours between the
    /// horizon crossings and the night is ten, so running the night on the day's scale leaves the sun at its
    /// nadir at the moment it should be rising - and then snapping to the horizon as the branch changes.
    /// Each half therefore gets its own denominator, which makes the sine continuous across both crossings.
    ///
    /// <para>
    /// Past <c>1</c> the sine is negative, putting the sun below the horizon, which is where the moon's
    /// mirrored rotation wants it.
    /// </para>
    /// </remarks>
    private float SolarProgress(float hourOfDay)
    {
      var cycle = _clock.Cycle;
      var sunrise = (float)cycle.SunriseHour;
      var sunset = (float)cycle.SunsetHour;

      if (hourOfDay >= sunrise && hourOfDay < sunset)
      {
        return (hourOfDay - sunrise) / (sunset - sunrise);
      }

      // Before sunrise is the tail of the previous night, so it counts on from that night's sunset rather
      // than restarting - otherwise the moon would cross the sky twice between dusk and dawn.
      var hour = hourOfDay < sunrise ? hourOfDay + cycle.HoursPerDay : hourOfDay;
      var nightHours = cycle.HoursPerDay - (sunset - sunrise);

      return 1.0f + (hour - sunset) / nightHours;
    }

    private void ApplySky(float daylight, float twilight)
    {
      if (_sky != null)
      {
        _sky.SkyTopColor = Blend(SkyTopNight, SkyTopDay, SkyTopTwilight, daylight, twilight);
        _sky.SkyHorizonColor = Blend(SkyHorizonNight, SkyHorizonDay, SkyHorizonTwilight, daylight, twilight);
        _sky.GroundHorizonColor = Blend(GroundNight, GroundDay, GroundTwilight, daylight, twilight);
      }

      // Fog is the loudest of the three. It is drawn over everything at distance, so a fog still lit for
      // midday is a pale band along the horizon of a night scene - which reads as the night not having
      // applied rather than as one setting having been missed.
      if (_environment != null)
      {
        _environment.FogLightColor = Blend(FogNight, FogDay, FogTwilight, daylight, twilight);
      }
    }

    /// <summary>Night to day by the light level, then toward the twilight cast by how close the crossover is.</summary>
    private static Color Blend(Color night, Color day, Color twilight, float daylight, float twilightWeight) =>
      night.Lerp(day, daylight).Lerp(twilight, twilightWeight);
  }
}
