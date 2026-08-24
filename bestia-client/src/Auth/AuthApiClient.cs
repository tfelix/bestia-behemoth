using System;
using System.Net.Http;
using System.Net.Http.Json;
using System.Text.Json.Serialization;
using System.Threading;
using System.Threading.Tasks;

namespace BestiaBehemothClient.Auth
{
  /// <summary>
  /// The two calls the game makes against the login server. Everything else in the passkey flow
  /// happens in the system browser.
  /// </summary>
  public sealed class AuthApiClient : IDisposable
  {
    private readonly HttpClient _http;

    public AuthApiClient(string baseUrl)
    {
      _http = new HttpClient
      {
        BaseAddress = new Uri(baseUrl.TrimEnd('/') + "/"),
        Timeout = TimeSpan.FromSeconds(10)
      };
    }

    public async Task<StartResponse> StartAsync(
      string redirectUri,
      string codeChallenge,
      string state,
      bool register,
      CancellationToken cancellationToken)
    {
      var request = new StartRequest
      {
        RedirectUri = redirectUri,
        CodeChallenge = codeChallenge,
        CodeChallengeMethod = "S256",
        State = state,
        Intent = register ? "REGISTER" : "LOGIN"
      };

      using var response = await _http
        .PostAsJsonAsync("api/v1/auth/game/start", request, cancellationToken)
        .ConfigureAwait(false);

      response.EnsureSuccessStatusCode();

      return await response.Content
        .ReadFromJsonAsync<StartResponse>(cancellationToken)
        .ConfigureAwait(false);
    }

    /// <summary>
    /// Trades the single-use code for the same zone JWT every other login method produces, so the
    /// rest of the connection sequence is unchanged.
    /// </summary>
    public async Task<string> ExchangeAsync(
      string code,
      string codeVerifier,
      CancellationToken cancellationToken)
    {
      var request = new ExchangeRequest
      {
        Code = code,
        CodeVerifier = codeVerifier
      };

      using var response = await _http
        .PostAsJsonAsync("api/v1/auth/game/exchange", request, cancellationToken)
        .ConfigureAwait(false);

      response.EnsureSuccessStatusCode();

      var body = await response.Content
        .ReadFromJsonAsync<ExchangeResponse>(cancellationToken)
        .ConfigureAwait(false);

      return body?.Token;
    }

    public void Dispose()
    {
      _http.Dispose();
    }

    private sealed class StartRequest
    {
      [JsonPropertyName("redirect_uri")]
      public string RedirectUri { get; set; }

      [JsonPropertyName("code_challenge")]
      public string CodeChallenge { get; set; }

      [JsonPropertyName("code_challenge_method")]
      public string CodeChallengeMethod { get; set; }

      [JsonPropertyName("state")]
      public string State { get; set; }

      [JsonPropertyName("intent")]
      public string Intent { get; set; }
    }

    public sealed class StartResponse
    {
      [JsonPropertyName("session_id")]
      public string SessionId { get; set; }

      [JsonPropertyName("login_url")]
      public string LoginUrl { get; set; }

      [JsonPropertyName("expires_in")]
      public long ExpiresIn { get; set; }
    }

    private sealed class ExchangeRequest
    {
      [JsonPropertyName("code")]
      public string Code { get; set; }

      [JsonPropertyName("code_verifier")]
      public string CodeVerifier { get; set; }
    }

    private sealed class ExchangeResponse
    {
      [JsonPropertyName("token")]
      public string Token { get; set; }
    }
  }
}
