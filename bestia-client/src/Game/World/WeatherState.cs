using BestiaBehemothClient.Bnet;
using BestiaBehemothClient.Bnet.Message;
using BestiaBehemothClient.Bnet.Message.Map;
using Godot;

namespace BestiaBehemothClient.Game.World
{
  /// <summary>
  /// Turns the server's weather into the handful of numbers the shaders and the scene actually want.
  /// </summary>
  /// <remarks>
  /// <c>WeatherSMSG</c> carries a kind, an intensity, a cloud cover, a wind and a temperature. What a shader
  /// needs is how wet the ground is and how much snow is lying, which are not the same thing - they are what
  /// the weather has *done* rather than what it is doing. What the scene needs is a third thing again: how
  /// hard it is falling *now*.
  ///
  /// <para>
  /// <b>That difference is the whole reason this class exists rather than a line in a shader.</b> Ground does
  /// not dry the instant rain stops, and snow does not vanish when it stops falling, so both are integrated
  /// over time and with different rates in each direction. A shader reading intensity directly would have
  /// puddles appear and disappear with the cloud cover. Precipitation runs on its own, much shorter constants
  /// for the mirror-image reason: particles driven off the accumulated value would keep raining for three
  /// minutes after the cloud had gone.
  /// </para>
  ///
  /// <para>
  /// The ground values go to global shader parameters rather than to the terrain material, so that water,
  /// foliage and anything else added later reads the same numbers instead of keeping its own copy - and so
  /// they can be driven by hand from Project Settings while there is no server to ask. The sky values are
  /// plain properties instead, polled per frame by <c>environment.gd</c>, <c>cloud_shadows.gd</c> and
  /// <c>precipitation.gd</c>, the way <see cref="WorldClock"/>'s light level already is: they change every
  /// frame, so a signal would fire every frame and save nobody anything.
  /// </para>
  ///
  /// <para>
  /// <see cref="Wind"/> is the one value that is both, and that is not a hedge. <c>cloud_shadows.gd</c> reads
  /// the property and integrates it into a drift; <c>grass.gdshader</c> reads the global, because it runs per
  /// vertex on every plant in the view and there is no path from GDScript to a MultiMesh's vertex shader
  /// short of a per-kind material uniform pushed on every weather message.
  /// </para>
  ///
  /// <para>
  /// <see cref="WeatherLook"/> holds the decisions - which kind means rain, how grey a sky goes. This holds
  /// the smoothing and the plumbing. It is forwarded field by field for the reason <see cref="WorldClock"/>
  /// forwards <see cref="WorldClock.SunriseHour"/>: a struct is not one of Godot's Variant types and cannot
  /// cross into GDScript.
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
    /// The same vector <see cref="Wind"/> reports, as a global shader parameter.
    /// </summary>
    /// <remarks>
    /// Published as well as exposed, rather than instead of, because the two have different readers. The
    /// property is polled per frame by <c>cloud_shadows.gd</c>, which integrates it into a drift; the global
    /// is read by <c>grass.gdshader</c>, which is a vertex shader on every plant in the view and has no way
    /// to be handed anything from GDScript at all.
    /// </remarks>
    private static readonly StringName WindParameter = "weather_wind";

    /// <summary>
    /// How far the gust field has travelled downwind, in radians of <see cref="GustReferenceMetres"/>.
    /// </summary>
    /// <remarks>
    /// <b>Integrated here rather than derived from <c>TIME</c> in the shader, and that is a bug fix.</b> The
    /// obvious form for a wave travelling downwind is <c>sin(dot(p, dir) - speed * TIME)</c>, and it is wrong
    /// whenever the speed is itself changing: differentiate it and the apparent rate is not <c>speed</c> but
    /// <c>speed + TIME * dspeed/dt</c>. <c>TIME</c> is seconds since the process started, so the error scales
    /// with how long the client has been open.
    ///
    /// <para>
    /// That is exactly the shape of the symptom it caused. This node is created by the <c>ConnectionManager</c>
    /// autoload at launch, but the first weather does not arrive until the player authenticates - minutes of
    /// menu later. A wind ramping to 6 m/s over <see cref="WindSeconds"/> at <c>TIME</c> = 120 s made the grass
    /// whip at ten times the speed it should, decaying over the following seconds as the ramp flattened.
    /// </para>
    ///
    /// <para>
    /// An integral has no such term, and it wraps, so it holds full float precision for a session of any
    /// length - which the metres form would not. <c>cloud_shadows.gd</c>'s own drift is the same integral for
    /// the same reason.
    /// </para>
    /// </remarks>
    private static readonly StringName GustPhaseParameter = "weather_gust_phase";

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

    /// <summary>
    /// How long the sky takes to change its mind, in seconds: the rate at which precipitation starts and stops.
    /// </summary>
    /// <remarks>
    /// Short, and symmetric, which is the opposite of the ground constants above and deliberately so. Rain
    /// stopping is an event the player watches happen over a few seconds; the ground drying afterwards is
    /// not. Long enough that a message arriving does not switch the rain on like a tap, short enough that a
    /// shower passing overhead reads as a shower passing overhead.
    /// </remarks>
    [Export] public float SkySeconds { get; set; } = 5.0f;

    /// <summary>
    /// How long the wind takes to swing round, in seconds.
    /// </summary>
    /// <remarks>
    /// Slower than the sky, because the cloud shadows drift on it: a wind that turned in five seconds would
    /// wheel the whole shadow field round in front of the player.
    /// </remarks>
    [Export] public float WindSeconds { get; set; } = 12.0f;

    /// <summary>
    /// The gust wavelength, in metres, that <see cref="GustPhaseParameter"/>'s radians are measured against.
    /// </summary>
    /// <remarks>
    /// Matches <c>grass.tres</c>'s <c>gust_wavelength</c>, and the two agreeing is what makes a gust cross the
    /// ground at exactly the wind's own speed. Nothing breaks if they drift apart - a shorter wavelength in
    /// the material simply makes its waves travel proportionally slower - so this is a look knob and not a
    /// contract. It is a number rather than a read of the material because the phase is one global serving
    /// every material that wants it, and there is no material to ask.
    /// </remarks>
    [Export] public float GustReferenceMetres { get; set; } = 9.0f;

    /// <summary>
    /// Ignores the server and renders the weather set below.
    /// </summary>
    /// <remarks>
    /// The remaining Debug properties do nothing while this is off. Here because every one of these effects
    /// is a look that has to be judged by eye, and the alternative is waiting for the world to produce a
    /// blizzard: the weather field is a pure function of (seed, region, time), so reaching a particular sky
    /// otherwise means scrubbing the calendar with <c>/date</c> until one turns up.
    /// </remarks>
    [Export] public bool DebugOverrideEnabled { get; set; }

    [Export] public WeatherSMSG.Kind DebugKind { get; set; } = WeatherSMSG.Kind.Clear;

    [Export(PropertyHint.Range, "0,1,0.01")] public float DebugIntensity { get; set; } = 0.6f;

    [Export(PropertyHint.Range, "0,1,0.01")] public float DebugCloudCover { get; set; } = 0.7f;

    [Export(PropertyHint.Range, "0,40,0.1")] public float DebugWindSpeed { get; set; } = 8.0f;

    [Export(PropertyHint.Range, "-180,180,1")] public float DebugWindDirectionDegrees { get; set; }

    [Export(PropertyHint.Range, "-40,45,0.5")] public float DebugTemperatureCelsius { get; set; } = 15.0f;

    private float _wetness;
    private float _snow;
    private float _temperature = 15.0f;

    private float _wetnessTarget;
    private float _snowTarget;
    private float _temperatureTarget = 15.0f;

    // The last reading the server sent, kept raw so the look can be rebuilt when a Debug knob is turned.
    private WeatherSMSG.Kind _kind = WeatherSMSG.Kind.Clear;
    private float _intensity;
    private float _cloudCover;
    private float _windSpeed;
    private float _windDirection;

    private float _rainRate;
    private float _snowRate;
    private float _dustRate;
    private float _overcast;
    private float _visibility = 1.0f;
    private float _hazeTint;
    private Vector3 _wind = Vector3.Zero;

    /// <summary>
    /// The wind as the rendering server last heard it, which is what decides whether a publish is due.
    /// </summary>
    /// <remarks>
    /// A second copy rather than testing <see cref="_wind"/> against its own new value, because that value has
    /// to be committed either way - see <see cref="_Process"/>. Starts at a deliberately impossible vector so
    /// that the first frame always publishes: the project setting's default is a light breeze, not zero, and
    /// a client that started in a dead calm would otherwise leave it standing.
    /// </remarks>
    private Vector3 _publishedWind = new(float.NaN, float.NaN, float.NaN);

    /// <summary>See <see cref="GustPhaseParameter"/>. Radians, wrapped, and a double so the wrap is exact.</summary>
    private double _gustPhase;

    /// <summary>
    /// Whether a reading has ever arrived, which is what decides between settling and transitioning.
    /// </summary>
    /// <remarks>
    /// The smoothing here is for weather <i>changing</i>, and every constant is chosen against that: five
    /// seconds for a shower starting, three minutes for ground to dry, twelve for the wind to swing round.
    /// None of them means anything for the first reading, because there is no previous sky to come from - the
    /// weather in a region was already happening before the player logged into it.
    ///
    /// <para>
    /// Left ramping, the first minute of every session was a lie in the player's favour or against it: walking
    /// into a downpour on dry ground that then slowly darkened, or a dead calm that gradually picked up. See
    /// <see cref="Settle"/>.
    /// </para>
    /// </remarks>
    private bool _seeded;

    private bool _attached;

    /// <summary>What the sky is doing, unsmoothed. Its look changes over time; its identity does not.</summary>
    public WeatherSMSG.Kind Kind
    {
      get { return DebugOverrideEnabled ? DebugKind : _kind; }
    }

    /// <summary>How hard rain is falling right now, 0 to 1. Zero unless it is actually raining.</summary>
    public float RainRate
    {
      get { return _rainRate; }
    }

    /// <summary>How hard snow is falling right now, 0 to 1.</summary>
    public float SnowRate
    {
      get { return _snowRate; }
    }

    /// <summary>How thick the airborne dust is right now, 0 to 1.</summary>
    public float DustRate
    {
      get { return _dustRate; }
    }

    /// <summary>How grey the sky is, 0 to 1. Not the reported cover - see <see cref="WeatherLook.Overcast"/>.</summary>
    public float Overcast
    {
      get { return _overcast; }
    }

    /// <summary>How far one can see, 1 on a clear day down to near zero in a whiteout.</summary>
    public float Visibility
    {
      get { return _visibility; }
    }

    /// <summary>
    /// Which way the weather is going and how fast, in metres per second, in Godot axes.
    /// </summary>
    /// <remarks>
    /// Smoothed as a vector rather than as a bearing. A wind swinging past due west crosses the wrap in the
    /// server's radians, and a smoothed angle would take the long way round - wheeling the entire cloud
    /// shadow field through a half turn in front of the player over the following few seconds.
    ///
    /// <para>
    /// The server sends a bearing with zero pointing east and turning counter-clockwise, in its own XY. Y
    /// there is Z here (see <c>Vec3Convert</c>), so east is +X and the bearing turns toward +Z.
    /// </para>
    /// </remarks>
    public Vector3 Wind
    {
      get { return _wind; }
    }

    /// <summary>Wind speed in metres per second, already carrying <see cref="WeatherLook.WindScale"/>.</summary>
    public float WindSpeed
    {
      get { return _wind.Length(); }
    }

    /// <summary>How much of the sunlight has become skylight - see <see cref="WeatherLook.Diffusion"/>.</summary>
    /// <remarks>
    /// This and the four below are the cover curves applied to the <b>smoothed</b> cover rather than read off
    /// <see cref="Look"/>, which is why <see cref="WeatherLook"/> carries a static form of each. A look is
    /// built from the raw reading and a reading is a step: read that way, the sun's disc crossed its whole
    /// range in a single frame while the sky that should have explained it eased over <see cref="SkySeconds"/>.
    /// <c>cloud_shadows.gd</c> already read <see cref="Overcast"/>, so the two halves of one effect ran on
    /// different clocks.
    ///
    /// <para>
    /// Everything still on <see cref="Look"/> below is a decision rather than a curve - which kind flashes,
    /// what colour its air goes - and a decision has nothing to smooth.
    /// </para>
    /// </remarks>
    public float Diffusion
    {
      get { return WeatherLook.DiffusionFor(_overcast); }
    }

    /// <summary>How strongly cloud shadows should read on the ground, 0 to 1, before the sun's height.</summary>
    public float ShadowStrength
    {
      get { return WeatherLook.ShadowStrengthFor(_overcast); }
    }

    /// <summary>What to multiply the sun's authored energy by.</summary>
    public float SunEnergyScale
    {
      get { return WeatherLook.SunEnergyScaleFor(_overcast); }
    }

    /// <summary>How wide to open the sun's angular diameter, in degrees, so its shadows soften.</summary>
    public float SunAngularDegrees
    {
      get { return WeatherLook.SunAngularDegreesFor(_overcast); }
    }

    /// <summary>How much of the authored twilight colour survives, 0 to 1.</summary>
    public float TwilightScale
    {
      get { return WeatherLook.TwilightScaleFor(_overcast); }
    }

    /// <summary>Whether this sky flashes.</summary>
    public bool HasLightning
    {
      get { return Look().HasLightning; }
    }

    /// <summary>What colour the air goes, for the kinds whose air has a colour.</summary>
    public Color HazeColour
    {
      get { return Look().HazeColour; }
    }

    /// <summary>
    /// How strongly <see cref="HazeColour"/> has taken over, 0 to 1, already carrying the visibility.
    /// </summary>
    /// <remarks>
    /// Smoothed rather than read straight off the look, because it multiplies a colour: an unsmoothed step
    /// would swap the fog from grey to sand between one frame and the next.
    /// </remarks>
    public float HazeTint
    {
      get { return _hazeTint; }
    }

    /// <summary>Whether anything is falling out of this sky right now.</summary>
    public bool IsPrecipitating
    {
      get { return _rainRate > 0.001f || _snowRate > 0.001f || _dustRate > 0.001f; }
    }

    /// <summary>Air temperature where the player stands, smoothed.</summary>
    public float TemperatureCelsius
    {
      get { return _temperature; }
    }

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

      _kind = weather.Weather;
      _intensity = weather.Intensity;
      _cloudCover = weather.CloudCover;
      _windSpeed = weather.WindSpeed;
      _windDirection = weather.WindDirection;
      _temperatureTarget = weather.TemperatureCelsius;

      if (_seeded)
      {
        return;
      }

      _seeded = true;
      Settle();
    }

    /// <summary>
    /// Jumps every smoothed value to what the current reading implies, with no transition.
    /// </summary>
    /// <remarks>
    /// Only ever called once, on the first reading - see <see cref="_seeded"/>. Every field here is one the
    /// smoothing in <see cref="_Process"/> would have converged to anyway; this only refuses to spend a minute
    /// getting there.
    ///
    /// <para>
    /// <see cref="_gustPhase"/> is deliberately not among them. It is a clock rather than a state, so there is
    /// nothing for it to settle to.
    /// </para>
    /// </remarks>
    private void Settle()
    {
      var look = Look();

      _wetnessTarget = look.GroundWetRate;
      _snowTarget = look.SnowRate;

      _wetness = _wetnessTarget;
      _snow = _snowTarget;
      _temperature = TargetTemperature();

      _rainRate = look.RainRate;
      _snowRate = look.SnowRate;
      _dustRate = look.DustRate;
      _overcast = look.Overcast;
      _visibility = look.Visibility;
      _hazeTint = look.HazeTint * (1.0f - look.Visibility);
      _wind = TargetWind(look);

      Publish();
    }

    public override void _Process(double delta)
    {
      var step = (float)delta;
      var look = Look();

      // Retargeted every frame rather than on message, so that turning a Debug knob in a running editor shows
      // up immediately - which is the only reason those knobs are worth having.
      _wetnessTarget = look.GroundWetRate;
      _snowTarget = look.SnowRate;

      var wetness = Approach(_wetness, _wetnessTarget, step, WettingSeconds, DryingSeconds);
      var snow = Approach(_snow, _snowTarget, step, SnowfallSeconds, ThawSeconds);
      var temperature = Approach(_temperature, TargetTemperature(), step, TemperatureSeconds, TemperatureSeconds);

      _rainRate = Approach(_rainRate, look.RainRate, step, SkySeconds, SkySeconds);
      _snowRate = Approach(_snowRate, look.SnowRate, step, SkySeconds, SkySeconds);
      _dustRate = Approach(_dustRate, look.DustRate, step, SkySeconds, SkySeconds);
      _overcast = Approach(_overcast, look.Overcast, step, SkySeconds, SkySeconds);
      _visibility = Approach(_visibility, look.Visibility, step, SkySeconds, SkySeconds);
      _hazeTint = Approach(_hazeTint, look.HazeTint * (1.0f - look.Visibility), step, SkySeconds, SkySeconds);

      // Assigned before the publish test below rather than after it, unlike the three that follow: this one is
      // read straight off the property every frame by cloud_shadows.gd, so holding it back on a frame that is
      // not worth publishing would stall the shadow drift rather than merely delay a shader parameter.
      _wind = ApproachVector(_wind, TargetWind(look), step, WindSeconds);

      // Advanced and published every frame, unlike everything below it, because this one is a clock: holding it
      // back on a frame is not a saved round trip but a stalled animation.
      _gustPhase = Mathf.Wrap(
        _gustPhase + Mathf.Tau * _wind.Length() * delta / Mathf.Max(GustReferenceMetres, 0.001f),
        0.0, Mathf.Tau);

      RenderingServer.GlobalShaderParameterSet(GustPhaseParameter, (float)_gustPhase);

      // Only publish on a change worth a round trip to the rendering server - the values are stable for minutes
      // at a time and this runs every frame. The sky values above are read as properties and need no publish.
      if (Mathf.IsEqualApprox(wetness, _wetness) &&
          Mathf.IsEqualApprox(snow, _snow) &&
          Mathf.IsEqualApprox(temperature, _temperature) &&
          _wind.IsEqualApprox(_publishedWind))
      {
        return;
      }

      _wetness = wetness;
      _snow = snow;
      _temperature = temperature;

      Publish();
    }

    /// <summary>The look for the current reading, or for the Debug knobs when they are in charge.</summary>
    private WeatherLook Look()
    {
      if (DebugOverrideEnabled)
      {
        return WeatherLook.For(DebugKind, DebugIntensity, DebugCloudCover);
      }

      return WeatherLook.For(_kind, _intensity, _cloudCover);
    }

    private float TargetTemperature()
    {
      return DebugOverrideEnabled ? DebugTemperatureCelsius : _temperatureTarget;
    }

    private Vector3 TargetWind(WeatherLook look)
    {
      var speed = DebugOverrideEnabled ? DebugWindSpeed : _windSpeed;
      var direction = DebugOverrideEnabled ? Mathf.DegToRad(DebugWindDirectionDegrees) : _windDirection;

      return new Vector3(Mathf.Cos(direction), 0.0f, Mathf.Sin(direction)) * speed * look.WindScale;
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

    /// <summary><see cref="Approach"/> componentwise, so a turning wind sweeps rather than wraps.</summary>
    private static Vector3 ApproachVector(Vector3 value, Vector3 target, float delta, float seconds)
    {
      return value.Lerp(target, 1.0f - Mathf.Exp(-delta / Mathf.Max(seconds, 0.001f)));
    }

    private void Publish()
    {
      RenderingServer.GlobalShaderParameterSet(Wetness, _wetness);
      RenderingServer.GlobalShaderParameterSet(Snow, _snow);
      RenderingServer.GlobalShaderParameterSet(Temperature, _temperature);
      RenderingServer.GlobalShaderParameterSet(WindParameter, _wind);

      _publishedWind = _wind;
    }
  }
}
