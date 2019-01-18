package com.kaviddiss.websocketmock.mock;

import com.kaviddiss.websocketmock.config.MockWebSocketProperties;
import com.kaviddiss.websocketmock.config.MockWebSocketProperties.Handler;
import com.kaviddiss.websocketmock.config.MockWebSocketProperties.WSMessage;
import com.kaviddiss.websocketmock.template.TemplateEngine;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.regex.PatternSyntaxException;
import javax.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageHandler;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.SubscribableChannel;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.GenericMessage;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

@Component
public class MockHandler implements MessageHandler {

  private static final String SERVER_HEADER = "server";
  private static final String WEBSOCKET_MOCK = "websocket-mock";
  private static final String STOMP_COMMAND = "stompCommand";
  private final Logger log = LoggerFactory.getLogger(getClass());

  private final MockWebSocketProperties properties;
  private final SubscribableChannel clientInboundChannel;
  private final MessageChannel clientOutboundChannel;
  private final ExecutorService executorService;
  private final MultiValueMap<String,String> subscriptions = new LinkedMultiValueMap<>();
  private final TemplateEngine templateEngine;

  public MockHandler(MockWebSocketProperties properties,
      SubscribableChannel clientInboundChannel,
      MessageChannel clientOutboundChannel, ExecutorService executorService,
      TemplateEngine templateEngine) {
    this.properties = properties;
    this.clientInboundChannel = clientInboundChannel;
    this.clientOutboundChannel = clientOutboundChannel;
    this.executorService = executorService;
    this.templateEngine = templateEngine;
  }

  @PostConstruct
  public void onInit() {
    clientInboundChannel.subscribe(this);
  }

  @Override
  public void handleMessage(Message<?> request) throws MessagingException {
    // ignore messages sent by us:
    if (WEBSOCKET_MOCK.equals(request.getHeaders().get(SERVER_HEADER))) {
      return;
    }

    printMessage(request, true);
    StompHeaderAccessor sha = createStompHeaderAccessor(request);

    Object stompCommand = request.getHeaders().get(STOMP_COMMAND);
    if (stompCommand == StompCommand.SUBSCRIBE) {
      String sessionId = SimpMessageHeaderAccessor.getSessionId(request.getHeaders());
      String subscriptionId = SimpMessageHeaderAccessor.getSubscriptionId(request.getHeaders());
      subscriptions.add(sessionId, subscriptionId);
    } else if (stompCommand == StompCommand.UNSUBSCRIBE) {
      String sessionId = SimpMessageHeaderAccessor.getSessionId(request.getHeaders());
      String subscriptionId = SimpMessageHeaderAccessor.getSubscriptionId(request.getHeaders());
      subscriptions.remove(sessionId, subscriptionId);
    }

    Handler handler = findHandler(request, sha);

    if (handler == null) {
      log.debug("No handler found for request: {} - payload: {}", request, payloadToString(request.getPayload()));
    } else {
      handleRequest(handler, request, sha);
    }
  }

  private static StompHeaderAccessor createStompHeaderAccessor(Message<?> request) {
    return MessageHeaderAccessor.getAccessor(request, StompHeaderAccessor.class);
  }


  private Handler findHandler(Message<?> request, StompHeaderAccessor sha) {
    Handler result = null;
    if (properties.getHandlers() == null) {
      log.error("No handlers configured!");
    } else {
      for (Handler handler : properties.getHandlers()) {
        if (checkCommand(request, handler.getRequest())
            && checkPayload(sha, handler.getRequest())
            && checkHeaders(sha, handler.getRequest())) {
          result = handler;
          break;
        }
      }
    }
    return result;
  }

  private void handleRequest(Handler handler, Message request, StompHeaderAccessor sha) {
    Map<String, Object> context = createContextFromRequest(request, handler);

    if (request.getHeaders().get(STOMP_COMMAND) == StompCommand.CONNECT) {
      Map<String, Object> headers = new HashMap<>();
      headers.putAll(handler.getResponse().getHeaders());
      headers.put(STOMP_COMMAND, handler.getResponse().getCommand());
      headers.put(SERVER_HEADER, WEBSOCKET_MOCK);

      Message response = new GenericMessage(getPayloadBytes(handler.getResponse(), context), headers);

      printMessage(response, false);
      clientInboundChannel.send(response);
    } else {
      String origDestination = sha.getDestination();
      if (handler.getResponse() != null) {
        sendResponse(handler.getResponse(), origDestination, context);
      }

      if (handler.getResponses() != null) {
        for (WSMessage response : handler.getResponses()) {
          sendResponse(response, origDestination, context);
        }
      }
    }
  }

  private Map<String, Object> createContextFromRequest(Message request, Handler handler) {
    Map<String, Object> context = new HashMap<>();
    if (handler.getRequest().getHeaders() != null) {
      StompHeaderAccessor sha = createStompHeaderAccessor(request);
      for (Map.Entry<String, String> entry : handler.getRequest().getHeaders().entrySet()) {
        templateEngine.readVariables(entry.getValue(),
            sha.getFirstNativeHeader(entry.getKey()), context);
      }
    }
    templateEngine.readVariables(handler.getRequest().getPayload(),
        payloadToString(request.getPayload()), context);
    return context;
  }

  private static String payloadToString(Object payload) {
    return payload instanceof byte[]
        ? new String((byte[]) payload)
        : String.valueOf(payload);
  }

  private void sendResponse(WSMessage wsMessage, String origDestination,
      Map<String, Object> context) {
    subscriptions.forEach((sessionId, subscriptionIds) -> {
      for (String subscriptionId : subscriptionIds) {
        Message response = createMessage(wsMessage, sessionId, subscriptionId, context);
        String destination = Optional
            .ofNullable(response.getHeaders().get("destination", String.class))
            .orElse(origDestination);

        executorService.submit(() -> {
          try {
            Thread.sleep(100L);
          } catch (InterruptedException e) {
          }

          printMessage(response, false);
          try {
            clientOutboundChannel.send(response);
          } catch (Exception e) {
            log.error("Failed to send response", e);
          }
        });
      }
    });
  }

  private static void printMessage(Message msg, boolean inbound) {
    System.out.println();
    System.out.println(inbound ? ">>>" : "<<<");
    System.out.println(msg.getHeaders().get(STOMP_COMMAND));
    StompHeaderAccessor sha = createStompHeaderAccessor(msg);
    Map<String, List> nativeHeaders = (Map<String, List>) msg.getHeaders().get("nativeHeaders");
    if (nativeHeaders != null) {
      for (Map.Entry<String, List> entry : nativeHeaders.entrySet()) {
        System.out.println(entry.getKey() + ": " + (entry.getValue().isEmpty() ? "" : entry.getValue().get(0)));
      }
    } else {
      for (Map.Entry<String, Object> entry : msg.getHeaders().entrySet()) {
        System.out.println(entry.getKey() + ": " + entry.getValue());
      }
    }
    System.out.println();
    System.out.println(payloadToString(msg.getPayload()));
  }

  private Message createMessage(WSMessage response, String sessionId, String subscriptionId,
      Map<String, Object> context) {
    Map<String, Object> headers = new HashMap<>();
    headers.put(STOMP_COMMAND, response.getCommand());
    for (Map.Entry<String, String> entry : response.getHeaders().entrySet()) {
      headers.put(entry.getKey(), templateEngine.compile(entry.getValue(), context));
    }
    headers.put(SimpMessageHeaderAccessor.SESSION_ID_HEADER, sessionId);
    headers.put(SimpMessageHeaderAccessor.SUBSCRIPTION_ID_HEADER, subscriptionId);
    headers.put(SERVER_HEADER, WEBSOCKET_MOCK);

    return new GenericMessage(getPayloadBytes(response, context), headers);
  }

  private byte[] getPayloadBytes(WSMessage response,
      Map<String, Object> context) {
    String payload = Optional.ofNullable(response.getPayload()).orElse("");
    payload = templateEngine.compile(payload, context);
    return payload.getBytes(Charset.forName("UTF-8"));
  }

  // using message as param, as sha is null for CONNECT messages:
  private boolean checkCommand(Message<?> message, WSMessage request) {
    boolean matches = request.getCommand() == null || request.getCommand() == message.getHeaders().get(
        STOMP_COMMAND);
    return matches;
  }

  private boolean checkPayload(StompHeaderAccessor sha, WSMessage request) {
    boolean matches = checkEqualsAndRegex(request.getPayload(), sha == null ? null : sha.getMessage());
    if (!matches) {
      log.debug("Invalid payload - expected: {}, actual: {}", request.getPayload(),
          sha.getMessage());
    }
    return matches;
  }

  private boolean checkHeaders(StompHeaderAccessor sha, WSMessage request) {
    boolean matches = true;
    if (request.getHeaders() != null) {
      for (Map.Entry<String, String> entry : request.getHeaders().entrySet()) {
        String nativeHeader = sha == null ? null : sha.getFirstNativeHeader(entry.getKey());
        if (!checkEqualsAndRegex(entry.getValue(), nativeHeader)) {
          log.debug("Invalid header {} - expected: {}, actual: {}",
              entry.getKey(), entry.getValue(), nativeHeader);
          matches = false;
        }
      }
    }
    return matches;
  }

  private boolean checkEqualsAndRegex(String expected, String actual) {
    boolean result = true;
    if (expected != null && actual != null) {
      try {
        result = actual.matches(expected);
      } catch (PatternSyntaxException e) {
        result = actual.equals(expected) || templateEngine.matches(expected, actual);
      }
    }
    return result;
  }
}
