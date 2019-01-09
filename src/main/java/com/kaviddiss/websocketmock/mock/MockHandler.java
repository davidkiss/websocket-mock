package com.kaviddiss.websocketmock.mock;

import com.kaviddiss.websocketmock.config.MockWebSocketProperties;
import com.kaviddiss.websocketmock.config.MockWebSocketProperties.Handler;
import com.kaviddiss.websocketmock.config.MockWebSocketProperties.WSMessage;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
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
  private final Logger log = LoggerFactory.getLogger(getClass());

  private final MockWebSocketProperties properties;
  private final SubscribableChannel clientInboundChannel;
  private final MessageChannel clientOutboundChannel;
  private final ExecutorService executorService;
  private final MultiValueMap<String,String> subscriptions = new LinkedMultiValueMap<>();

  public MockHandler(MockWebSocketProperties properties,
      SubscribableChannel clientInboundChannel,
      MessageChannel clientOutboundChannel, ExecutorService executorService) {
    this.properties = properties;
    this.clientInboundChannel = clientInboundChannel;
    this.clientOutboundChannel = clientOutboundChannel;
    this.executorService = executorService;
  }

  @PostConstruct
  public void onInit() {
    clientInboundChannel.subscribe(this);
  }

  @Override
  public void handleMessage(Message<?> request) throws MessagingException {
    log.info("Handling message: {}", request);
    StompHeaderAccessor sha =
        MessageHeaderAccessor.getAccessor(request, StompHeaderAccessor.class);

    Object stompCommand = request.getHeaders().get("stompCommand");
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
      log.warn("No handler found for request: {}", request);
    } else {
      handleRequest(handler, request, sha);
    }
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
    if (request.getHeaders().get("stompCommand") == StompCommand.CONNECT) {
      Map<String, Object> headers = new HashMap<>();
      headers.putAll(handler.getResponse().getHeaders());
      headers.put("stompCommand", handler.getResponse().getCommand());

      Message response = new GenericMessage(getPayloadBytes(handler.getResponse()), headers);
      log.info("Sending response: {}", response);
      clientInboundChannel.send(response);
    } else {
      String origDestination = sha.getDestination();
      if (handler.getResponse() != null) {
        sendResponse(handler.getResponse(), origDestination);
      }

      if (handler.getResponses() != null) {
        for (WSMessage response : handler.getResponses()) {
          sendResponse(response, origDestination);
        }
      }
    }
  }

  private void sendResponse(WSMessage wsMessage, String origDestination) {
    subscriptions.forEach((sessionId, subscriptionIds) -> {
      for (String subscriptionId : subscriptionIds) {
        Message response = createMessage(wsMessage, sessionId, subscriptionId);
        String destination = Optional
            .ofNullable(response.getHeaders().get("destination", String.class))
            .orElse(origDestination);

        executorService.submit(() -> {
          try {
            Thread.sleep(100L);
          } catch (InterruptedException e) {
          }

          log.info("Sending response to {}: {}", destination, response);
          clientOutboundChannel.send(response);
        });
      }
    });
  }

  private Message createMessage(WSMessage response, String sessionId, String subscriptionId) {
    Map<String, Object> headers = new HashMap<>();
    headers.put("stompCommand", response.getCommand());
    headers.putAll(response.getHeaders());
    headers.put(SimpMessageHeaderAccessor.SESSION_ID_HEADER, sessionId);
    headers.put(SimpMessageHeaderAccessor.SUBSCRIPTION_ID_HEADER, subscriptionId);

    return new GenericMessage(getPayloadBytes(response), headers);
  }

  private byte[] getPayloadBytes(WSMessage response) {
    return Optional.ofNullable(response.getPayload()).orElse("").getBytes(Charset.forName("UTF-8"));
  }

  // using message as param, as sha is null for CONNECT messages:
  private boolean checkCommand(Message<?> message, WSMessage request) {
    boolean matches = request.getCommand() == null || request.getCommand() == message.getHeaders().get("stompCommand");
    return matches;
  }

  private boolean checkPayload(StompHeaderAccessor sha, WSMessage request) {
    boolean matches = checkEqualsAndRegex(request.getPayload(), sha == null ? null : sha.getMessage());
    if (!matches) {
      log.warn("Invalid payload - expected: {}, actual: {}", request.getPayload(),
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
          log.warn("Invalid header {} - expected: {}, actual: {}",
              entry.getKey(), entry.getValue(), nativeHeader);
          matches = false;
        }
      }
    }
    return matches;
  }

  private boolean checkEqualsAndRegex(String expected, String actual) {
    return expected == null || (actual != null && actual.matches(expected));
  }
}
