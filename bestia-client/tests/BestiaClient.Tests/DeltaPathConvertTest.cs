using System.Collections.Generic;
using BestiaBehemothClient.Bnet.Message;
using Bnet;
using Xunit;

namespace BestiaBehemothClient.Tests
{
  public class DeltaPathConvertTest
  {
    [Fact]
    public void A_path_survives_the_round_trip_uphill_and_down()
    {
      var path = new List<Vec3>
      {
        new() { X = 100, Y = -4, Z = 12 },
        new() { X = 101, Y = -5, Z = 13 },
        new() { X = 101, Y = -6, Z = 11 },
      };

      Assert.Equal(path, DeltaPathConvert.Decode(DeltaPathConvert.Encode(path)));
    }

    [Fact]
    public void An_empty_path_is_sent_as_no_path()
    {
      var encoded = DeltaPathConvert.Encode(new List<Vec3>());

      Assert.Null(encoded.First);
      Assert.Empty(DeltaPathConvert.Decode(encoded));
    }

    [Fact]
    public void A_missing_path_decodes_to_no_steps()
    {
      Assert.Empty(DeltaPathConvert.Decode(null));
    }

    [Fact]
    public void The_bytes_match_what_the_server_decodes()
    {
      // The same three steps as zone-server's DeltaPathsTest: first waypoint, then dx, dy, dz per step.
      var encoded = DeltaPathConvert.Encode(new List<Vec3>
      {
        new() { X = 0, Y = 0, Z = 0 },
        new() { X = 1, Y = 1, Z = 0 },
        new() { X = 2, Y = 2, Z = 1 },
      });

      Assert.Equal(new[] { 1, 1, 0, 1, 1, 1 }, encoded.Deltas);
    }
  }
}
