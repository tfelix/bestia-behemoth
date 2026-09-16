using System;
using System.Collections.Generic;
using System.Globalization;
using System.Threading;
using BestiaBehemothClient.Bnet.Message;
using Bnet;
using Xunit;

namespace BestiaBehemothClient.Tests
{
  /// <summary>
  /// Name selection for the network log's detail lines.
  /// </summary>
  public class MessageFilterTest
  {
    [Fact]
    public void No_entries_at_all_allows_everything()
    {
      Assert.True(MessageFilter.Parse(null).Allows("comp_position"));
      Assert.True(MessageFilter.Parse(Array.Empty<string>()).Allows("comp_position"));
    }

    [Fact]
    public void A_bare_name_excludes_everything_it_does_not_name()
    {
      var filter = MessageFilter.Parse(new[] { "comp_path" });

      Assert.True(filter.Allows("comp_path"));
      Assert.False(filter.Allows("comp_position"));
    }

    [Fact]
    public void A_denied_name_loses_even_when_it_is_also_allowed()
    {
      var filter = MessageFilter.Parse(new[] { "comp_path", "!comp_path" });

      Assert.False(filter.Allows("comp_path"));
    }

    /// <summary>
    /// The departure from zone-server, which tests its entries as substrings of the rendered message.
    /// </summary>
    [Fact]
    public void A_name_is_matched_whole_rather_than_as_a_substring()
    {
      var filter = MessageFilter.Parse(new[] { "!comp_position" });

      Assert.True(filter.Allows("comp_position_extra"));
      Assert.False(filter.Allows("comp_position"));
    }
  }

  /// <summary>
  /// The per-second traffic summary, and the cap that keeps the console usable however the filter is set.
  /// </summary>
  public class NetLogTest
  {
    public NetLogTest()
    {
      NetLog.FlushInterval = TimeSpan.Zero;
      NetLog.Mode = NetLogMode.Summary;
      NetLog.SetFilter(null);
      NetLog.Sink = _ => { };
      NetLog.Tick();
    }

    private static List<string> Collect()
    {
      var lines = new List<string>();
      NetLog.Sink = lines.Add;

      return lines;
    }

    [Fact]
    public void A_window_leads_with_its_totals_and_the_depth_the_queue_reached()
    {
      var line = NetLog.FormatWindow("RX", 1.0, new[]
      {
        new TypeTally("comp_position", 847, 12_400),
        new TypeTally("comp_path", 93, 2_150)
      }, 42);

      Assert.Equal(
        "NET RX 1.0s: 940 msgs 14.2 KB, peak queue 42 | comp_position x847 12.1 KB, comp_path x93 2.1 KB",
        line);
    }

    /// <summary>Only the receive side queues, so the send line has no depth to report.</summary>
    [Fact]
    public void A_window_without_a_queue_says_nothing_about_one()
    {
      var line = NetLog.FormatWindow("TX", 1.0, new[] { new TypeTally("ping", 1, 4) }, null);

      Assert.Equal("NET TX 1.0s: 1 msgs 4 B | ping x1 4 B", line);
    }

    [Fact]
    public void A_window_nothing_arrived_in_says_nothing()
    {
      Assert.Null(NetLog.FormatWindow("RX", 1.0, Array.Empty<TypeTally>(), null));
    }

    /// <summary>
    /// A login touches some forty message types. Listing all of them would put the flood back at one hertz.
    /// </summary>
    [Fact]
    public void A_long_tail_of_types_is_counted_rather_than_listed()
    {
      var tallies = new List<TypeTally>();
      for (var i = 0; i < 9; i++)
      {
        tallies.Add(new TypeTally($"type_{i}", 9 - i, 10));
      }

      var line = NetLog.FormatWindow("RX", 1.0, tallies, null);

      Assert.Contains("type_0 x9", line);
      Assert.Contains("type_5 x4", line);
      Assert.DoesNotContain("type_6", line);
      Assert.EndsWith("(+3 types)", line);
    }

    /// <summary>
    /// These lines get pasted into bug reports and compared with each other, and the machines that produce
    /// them do not agree on what a decimal separator is.
    /// </summary>
    [Fact]
    public void Numbers_read_the_same_whatever_locale_the_client_runs_in()
    {
      var previous = Thread.CurrentThread.CurrentCulture;
      Thread.CurrentThread.CurrentCulture = new CultureInfo("de-DE");
      try
      {
        var line = NetLog.FormatWindow("RX", 1.2, new[] { new TypeTally("comp_path", 1, 2_150) }, null);

        Assert.Contains("1.2s", line);
        Assert.Contains("2.1 KB", line);
      }
      finally
      {
        Thread.CurrentThread.CurrentCulture = previous;
      }
    }

    [Fact]
    public void Summary_mode_keeps_single_messages_off_the_console()
    {
      var lines = Collect();
      NetLog.Mode = NetLogMode.Summary;

      NetLog.TraceRx(new Envelope { CompPath = new PathComponentSMSG { EntityId = 7 } });

      Assert.Empty(lines);
    }

    [Fact]
    public void Detail_mode_reports_only_what_the_filter_allows()
    {
      var lines = Collect();
      NetLog.Mode = NetLogMode.Detail;
      NetLog.SetFilter(new[] { "comp_path" });

      NetLog.TraceRx(new Envelope { CompPath = new PathComponentSMSG { EntityId = 7 } });
      NetLog.TraceRx(new Envelope { CompPosition = new PositionComponent { EntityId = 7 } });

      Assert.Single(lines);
      Assert.StartsWith("NET RX comp_path ", lines[0]);
    }

    /// <summary>
    /// The guarantee the whole class rests on: a filter left too wide must not be able to flood Godot,
    /// and must not swallow the evidence that it was too wide either.
    /// </summary>
    [Fact]
    public void A_filter_left_too_wide_is_capped_and_owned_up_to()
    {
      var lines = Collect();
      NetLog.Mode = NetLogMode.Detail;

      var position = new Envelope { CompPosition = new PositionComponent { EntityId = 7 } };
      for (var i = 0; i < 500; i++)
      {
        NetLog.TraceRx(position);
      }

      Assert.Equal(120, lines.Count);

      lines.Clear();
      NetLog.Tick();

      Assert.Contains(lines, line => line.Contains("380 detail lines suppressed"));
    }

    [Fact]
    public void A_flushed_window_lets_the_detail_lines_start_again()
    {
      var lines = Collect();
      NetLog.Mode = NetLogMode.Detail;

      var position = new Envelope { CompPosition = new PositionComponent { EntityId = 7 } };
      for (var i = 0; i < 500; i++)
      {
        NetLog.TraceRx(position);
      }

      NetLog.Tick();
      lines.Clear();
      NetLog.TraceRx(position);

      Assert.Single(lines);
    }

    [Fact]
    public void The_authentication_token_is_never_spelled_out()
    {
      var envelope = new Envelope
      {
        Authentication = new global::Bnet.Authentication
        {
          Token = "eyJ.super-secret.sig",
          ClientVersion = "0.0.1"
        }
      };

      var described = NetLog.Describe(envelope);

      Assert.DoesNotContain("super-secret", described);
      Assert.Contains("20 chars", described);
    }
  }
}
