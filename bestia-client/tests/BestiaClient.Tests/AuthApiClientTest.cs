using System;
using System.Net;
using System.Net.Http;
using System.Net.Sockets;
using System.Text;
using System.Threading;
using System.Threading.Tasks;
using BestiaBehemothClient.Auth;
using Xunit;

namespace BestiaBehemothClient.Tests
{
  /// <summary>
  /// A refused refresh makes the client throw its token away, so a rebirth must not look like one: it has to be told
  /// apart from every other answer.
  /// </summary>
  public class AuthApiClientTest
  {
    [Fact]
    public async Task ARebirthIsNotARefusal()
    {
      using var server = new OneAnswerServer(503, "{\"error\":\"WORLD_REBIRTH\",\"message\":null}");
      using var api = new AuthApiClient(server.Url);

      await Assert.ThrowsAsync<WorldRebirthException>(() => api.RefreshAsync("token", CancellationToken.None));
    }

    [Fact]
    public async Task AnyOtherOutageIsATransportFailure()
    {
      using var server = new OneAnswerServer(503, "Service Unavailable");
      using var api = new AuthApiClient(server.Url);

      await Assert.ThrowsAsync<HttpRequestException>(() => api.RefreshAsync("token", CancellationToken.None));
    }

    [Fact]
    public async Task ARefusedTokenIsStillARefusal()
    {
      using var server = new OneAnswerServer(400, "{\"error\":\"INVALID_GRANT\",\"message\":null}");
      using var api = new AuthApiClient(server.Url);

      Assert.Null(await api.RefreshAsync("token", CancellationToken.None));
    }

    /// <summary>Answers every request on a free loopback port the same way.</summary>
    private sealed class OneAnswerServer : IDisposable
    {
      private readonly HttpListener _listener = new();

      public OneAnswerServer(int status, string body)
      {
        var probe = new TcpListener(IPAddress.Loopback, 0);
        probe.Start();
        var port = ((IPEndPoint)probe.LocalEndpoint).Port;
        probe.Stop();

        Url = $"http://127.0.0.1:{port}/";
        _listener.Prefixes.Add(Url);
        _listener.Start();
        _ = Task.Run(async () =>
        {
          while (_listener.IsListening)
          {
            HttpListenerContext context;
            try
            {
              context = await _listener.GetContextAsync();
            }
            catch (Exception)
            {
              return;
            }

            var bytes = Encoding.UTF8.GetBytes(body);
            context.Response.StatusCode = status;
            context.Response.ContentType = "application/json";
            await context.Response.OutputStream.WriteAsync(bytes);
            context.Response.Close();
          }
        });
      }

      public string Url { get; }

      public void Dispose()
      {
        _listener.Stop();
        _listener.Close();
      }
    }
  }
}
