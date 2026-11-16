import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.tjake.jlama.model.AbstractModel;
import com.github.tjake.jlama.model.ModelSupport;
import com.github.tjake.jlama.model.functions.Generator;
import com.github.tjake.jlama.safetensors.DType;
import com.github.tjake.jlama.safetensors.prompt.PromptContext;
import com.github.tjake.jlama.safetensors.prompt.PromptSupport;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.File;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * Pure-JVM inference behind the same chat endpoint llama-server offers, so prose.py drives both runtimes.
 *
 * Only what the spike uses: messages, max_tokens and temperature in; text and timings out. One request at a
 * time, like the llama.cpp sidecar with one slot.
 *
 * Usage: JlamaServer <model dir> [port]
 */
public final class JlamaServer {

  private static final ObjectMapper JSON = new ObjectMapper();

  public static void main(String[] args) throws Exception {
    File modelDir = new File(args[0]);
    int port = args.length > 1 ? Integer.parseInt(args[1]) : 8081;

    long loadStart = System.currentTimeMillis();
    AbstractModel model = ModelSupport.loadModel(modelDir, DType.F32, DType.I8);
    System.out.printf("model loaded in %d ms%n", System.currentTimeMillis() - loadStart);

    HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", port), 0);
    server.createContext("/health", exchange -> respond(exchange, 200, "{\"status\":\"ok\"}"));
    server.createContext("/v1/chat/completions", exchange -> {
      try {
        respond(exchange, 200, complete(model, JSON.readTree(exchange.getRequestBody())));
      } catch (Exception e) {
        e.printStackTrace();
        respond(exchange, 500, JSON.createObjectNode().put("error", String.valueOf(e)).toString());
      }
    });
    server.start();
    System.out.println("listening on " + port);
  }

  private static synchronized String complete(AbstractModel model, JsonNode request) {
    PromptSupport.Builder prompt = model.promptSupport().orElseThrow().builder();
    for (JsonNode message : request.get("messages")) {
      String content = message.get("content").asText();
      switch (message.get("role").asText()) {
        case "system" -> prompt.addSystemMessage(content);
        case "assistant" -> prompt.addAssistantMessage(content);
        default -> prompt.addUserMessage(content);
      }
    }
    PromptContext context = prompt.build();
    float temperature = (float) request.path("temperature").asDouble(0.7);
    // Jlama's limit counts the prompt too, llama-server's only the new tokens.
    int promptTokens = model.getTokenizer().encode(context.getPrompt()).length;
    int maxTokens = promptTokens + request.path("max_tokens").asInt(400);

    Generator.Response response = model.generate(UUID.randomUUID(), context, temperature, maxTokens, (token, time) -> { });

    ObjectNode body = JSON.createObjectNode();
    body.putArray("choices").addObject().putObject("message")
        .put("role", "assistant").put("content", response.responseText);
    // The same field names llama-server uses, so one harness reads both.
    body.putObject("timings")
        .put("prompt_n", response.promptTokens)
        .put("prompt_ms", response.promptTimeMs)
        .put("prompt_per_second", perSecond(response.promptTokens, response.promptTimeMs))
        .put("predicted_n", response.generatedTokens)
        .put("predicted_ms", response.generateTimeMs)
        .put("predicted_per_second", perSecond(response.generatedTokens, response.generateTimeMs));
    return body.toString();
  }

  private static double perSecond(long tokens, long millis) {
    return millis == 0 ? 0 : tokens * 1000.0 / millis;
  }

  private static void respond(HttpExchange exchange, int status, String body) throws java.io.IOException {
    byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
    exchange.getResponseHeaders().add("Content-Type", "application/json");
    exchange.sendResponseHeaders(status, bytes.length);
    exchange.getResponseBody().write(bytes);
    exchange.close();
  }
}
