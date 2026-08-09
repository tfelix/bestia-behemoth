using BestiaBehemothClient.Bnet;
using BestiaBehemothClient.Bnet.Message;
using BestiaBehemothClient.Bnet.Message.Map;
using Godot;

namespace BestiaBehemothClient.Game.World
{
  /// <summary>
  /// The world calendar, anchored once by the server and run forward here.
  /// </summary>
  /// <remarks>
  /// <c>WorldInfoSMSG</c> carries how old the world is in Bestia-seconds and how fast Bestia time runs; this
  /// adds elapsed frame time to the first at the rate of the second. So the clock ticks smoothly off one
  /// message per connection rather than needing one per second per player, and it never reads the local
  /// machine's own clock - a player whose PC thinks it is 1970 sees the same in-game date as everybody else.
  ///
  /// <para>
  /// <b>The calendar's shape is the server's to state.</b> Hours per day, days per month and months per year
  /// all arrive on the wire rather than being constants here, for the reason the block palette's ordinals are
  /// a stated wire format: a second copy of them would go on producing a plausible date after the server's
  /// changed, and nobody would report a date that merely looks wrong.
  /// </para>
  ///
  /// <para>
  /// Attached the way <see cref="WeatherState"/> and <c>ChunkStreamManager</c> are, and for their reason: the
  /// world info arrives on authentication, which is long before the Game scene exists.
  /// </para>
  /// </remarks>
  [GlobalClass]
  public partial class WorldClock : Node
  {
    /// <summary>
    /// The reading changed, to the resolution anything displays it at.
    /// </summary>
    /// <remarks>
    /// Emitted per in-game minute rather than per frame - at the default speed factor that is one signal every
    /// twenty real seconds. <paramref name="season"/> is a <c>Season</c> ordinal; the caller names it, because
    /// what a season is called is a question for whatever is showing it and what it *is* is a question for the
    /// calendar.
    /// </remarks>
    [Signal]
    public delegate void TimeChangedEventHandler(
      long year, int month, int day, int hour, int minute, int season, bool isNight);

    /// <summary>Spring, summer, fall, winter - the ordinals <see cref="TimeChangedEventHandler"/> emits.</summary>
    public enum Season
    {
      Spring = 0,
      Summer = 1,
      Fall = 2,
      Winter = 3
    }

    private const double SecondsPerHour = 3600.0;

    private double _bestiaSeconds;
    private double _speedFactor = 1.0;

    private int _hoursPerDay;
    private int _daysPerMonth;
    private int _monthsPerYear;

    private DayCycle _cycle;

    private bool _anchored;

    /// <summary>The minute last broadcast, so the signal fires on a change rather than on a frame.</summary>
    private long _publishedMinute = -1;

    /// <summary>
    /// Starts listening for the world info.
    /// </summary>
    /// <remarks>
    /// Never unsubscribed, and that is correct rather than overlooked: ConnectionManager builds this once and
    /// keeps it for the process, and the socket it subscribes to outlives every scene.
    /// </remarks>
    public void Attach(BnetSocket socket)
    {
      if (socket == null)
      {
        GD.PushWarning("WorldClock has no BnetSocket; the world calendar will stay unset.");
        return;
      }

      socket.MessageReceived += OnMessageReceived;
    }

    private void OnMessageReceived(ISMSG message)
    {
      if (message is WorldInfoSMSG info)
      {
        Anchor(info);
      }
      else if (message is WorldTimeSMSG time)
      {
        Reanchor(time);
      }
    }

    /// <summary>
    /// Sets the clock from a world info message, or refuses it and says why.
    /// </summary>
    /// <remarks>
    /// Separate from the socket callback so the calendar arithmetic can be driven without a server, which is
    /// the only way to check a date that is three in-game years away from anything a session will reach.
    /// </remarks>
    public void Anchor(WorldInfoSMSG info)
    {
      // A world whose calendar has no shape cannot be counted in, and a zero speed factor is a stopped clock.
      // Both mean an older server, so say so once and leave the widget hidden rather than showing Year 1.
      if (info.HoursPerDay <= 0 || info.DaysPerMonth <= 0 || info.MonthsPerYear <= 0 || info.TimeSpeedFactor <= 0.0)
      {
        GD.PushWarning(
          "[clock] WorldInfoSMSG carried no calendar (hours/day, days/month, months/year or speed factor was " +
          "zero). The server predates the world clock; no in-game time will be shown.");

        return;
      }

      _bestiaSeconds = info.WorldAgeBestiaSeconds;
      _speedFactor = info.TimeSpeedFactor;
      _hoursPerDay = info.HoursPerDay;
      _daysPerMonth = info.DaysPerMonth;
      _monthsPerYear = info.MonthsPerYear;
      _cycle = new DayCycle(
        info.HoursPerDay, info.NightEndHour, info.DawnEndHour, info.DuskStartHour, info.NightStartHour);
      _anchored = true;

      if (!_cycle.IsValid)
      {
        // Not fatal, and not lumped in with the refusal above: the calendar itself is fine, so the date is
        // still worth showing. Only the lighting has nothing to go on, and it falls back to full day.
        GD.PushWarning(
          "[clock] WorldInfoSMSG's day boundaries are not in order " +
          $"(night ends {info.NightEndHour}, dawn ends {info.DawnEndHour}, dusk starts {info.DuskStartHour}, " +
          $"night starts {info.NightStartHour}, day is {info.HoursPerDay}h). The world will stay lit as day.");
      }

      // Forces the next _Process to emit, so a reconnect that lands on the same minute still refreshes.
      _publishedMinute = -1;
    }

    /// <summary>
    /// Moves the reading to one the server has just jumped to, keeping the calendar's shape.
    /// </summary>
    /// <remarks>
    /// The shape - hours per day, days per month, months per year, night hours - is the world's identity and
    /// does not move, so it stays where it was stated, on <c>WorldInfoSMSG</c>. Only the reading is here. See
    /// <see cref="WorldTimeSMSG"/> for why this is not simply another world info.
    ///
    /// <para>
    /// Ignored before an anchor exists, rather than treated as one. A reading with no calendar to interpret
    /// it against would divide by a zero-hour day; and this cannot legitimately arrive first, because the
    /// world info is sent the moment a connection authenticates.
    /// </para>
    /// </remarks>
    public void Reanchor(WorldTimeSMSG time)
    {
      if (!_anchored)
      {
        GD.PushWarning("[clock] a world time arrived before the world info; ignoring it.");

        return;
      }

      if (time.TimeSpeedFactor <= 0.0)
      {
        GD.PushWarning("[clock] WorldTimeSMSG carried a zero speed factor; keeping the current clock rate.");
      }
      else
      {
        _speedFactor = time.TimeSpeedFactor;
      }

      _bestiaSeconds = time.WorldAgeBestiaSeconds;

      // The jump is almost never a whole in-game minute, so publish it now rather than at the next rollover -
      // twenty real seconds of the HUD showing the old time reads as the command having done nothing.
      Publish(force: true);
    }

    public override void _Process(double delta)
    {
      if (!_anchored)
      {
        return;
      }

      _bestiaSeconds += delta * _speedFactor;

      Publish(force: false);
    }

    /// <summary>
    /// Re-emits <see cref="TimeChangedEventHandler"/> for whatever the time is now.
    /// </summary>
    /// <remarks>
    /// For a display that was created after the clock was anchored, which is every display: the world info
    /// arrives on authentication and the HUD is built when a master is chosen. Without this the clock face
    /// would stay blank until the in-game minute happened to roll over.
    /// </remarks>
    public void PublishNow() => Publish(force: true);

    /// <summary>Whether the server has said what time it is yet.</summary>
    public bool IsAnchored() => _anchored;

    /// <summary>
    /// The shape of one day, as the server stated it. Not <see cref="DayCycle.IsValid"/> until anchored.
    /// </summary>
    /// <remarks>
    /// Exposed for <see cref="DayNightCycle"/>, which needs the boundaries to place the sun's arc: sunrise is
    /// the midpoint of the dawn ramp, which is a question about the calendar rather than one a renderer may
    /// answer for itself.
    /// </remarks>
    public DayCycle Cycle => _cycle;

    /// <summary>
    /// The hour of the Bestia day as a fraction, e.g. <c>13.5</c> for half past one in the afternoon.
    /// </summary>
    /// <remarks>
    /// Continuous, and read per frame rather than delivered by
    /// <see cref="TimeChangedEventHandler"/> - that fires once an in-game minute, which is about every twenty
    /// real seconds, and a sun that moved twenty seconds at a time would not look like a sun.
    /// </remarks>
    public double HourOfDay
    {
      get
      {
        if (!_anchored)
        {
          return 0.0;
        }

        var secondsPerDay = _hoursPerDay * SecondsPerHour;

        return Mathf.PosMod(_bestiaSeconds, secondsPerDay) / SecondsPerHour;
      }
    }

    /// <summary>
    /// How much of the sun's light is up, in <c>[0, 1]</c>: <c>1</c> in full day, <c>0</c> in full night, and
    /// a smooth ramp across dawn and dusk.
    /// </summary>
    /// <remarks>
    /// The curve itself lives on <see cref="DayCycle"/>, which has no engine in it so a test can check it
    /// against the server's. This is only the clock reading fed into it.
    /// </remarks>
    public double Daylight => _cycle.DaylightAt(HourOfDay);

    /// <summary>True during full night, i.e. the dark middle rather than anything short of noon.</summary>
    public bool IsNight => _anchored && _cycle.IsNightAt(HourOfDay);

    private void Publish(bool force)
    {
      if (!_anchored)
      {
        return;
      }

      var total = (long)_bestiaSeconds;
      var minute = total / 60;

      if (!force && minute == _publishedMinute)
      {
        return;
      }

      _publishedMinute = minute;

      var secondsPerDay = (long)(_hoursPerDay * SecondsPerHour);
      var daysPerYear = (long)_daysPerMonth * _monthsPerYear;

      var days = total / secondsPerDay;
      var intoDay = total % secondsPerDay;

      var dayOfYear = days % daysPerYear;

      EmitSignal(
        SignalName.TimeChanged,
        (days / daysPerYear) + 1,
        (int)(dayOfYear / _daysPerMonth) + 1,
        (int)(dayOfYear % _daysPerMonth) + 1,
        (int)(intoDay / (long)SecondsPerHour),
        (int)(intoDay % (long)SecondsPerHour / 60),
        (int)SeasonOfMonth((int)(dayOfYear / _daysPerMonth) + 1),
        IsNight);
    }

    /// <summary>
    /// Which season a 1-indexed month falls in.
    /// </summary>
    /// <remarks>
    /// Spelled out rather than cast from the month, mirroring <c>Season.ofMonth</c> on the server - the two
    /// have to agree, and the server's own comment records that an index into a declaration order is exactly
    /// the conflation that put summer next to winter once already.
    ///
    /// <para>
    /// Falls back to spring for a calendar that is not four months long. That is a world this widget cannot
    /// label correctly, and a wrong season name is worse than a constant one nobody trusts.
    /// </para>
    /// </remarks>
    private Season SeasonOfMonth(int month) => month switch
    {
      1 => Season.Spring,
      2 => Season.Summer,
      3 => Season.Fall,
      4 => Season.Winter,
      _ => Season.Spring
    };
  }
}
