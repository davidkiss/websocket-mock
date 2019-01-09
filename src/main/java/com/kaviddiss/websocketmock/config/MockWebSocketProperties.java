package com.kaviddiss.websocketmock.config;

import java.util.List;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "mock")
public class MockWebSocketProperties {
  public static class Handler {
    private WSMessage request;
    private WSMessage response;
    private List<WSMessage> responses;

    public WSMessage getRequest() {
      return request;
    }

    public void setRequest(WSMessage request) {
      this.request = request;
    }

    public WSMessage getResponse() {
      return response;
    }

    public void setResponse(WSMessage response) {
      this.response = response;
    }

    public List<WSMessage> getResponses() {
      return responses;
    }

    public void setResponses(
        List<WSMessage> responses) {
      this.responses = responses;
    }
  }

  public static class WSMessage {
    private StompCommand command;
    private Map<String, String> headers;
    private String payload;

    public StompCommand getCommand() {
      return command;
    }

    public void setCommand(StompCommand command) {
      this.command = command;
    }

    public Map<String, String> getHeaders() {
      return headers;
    }

    public void setHeaders(Map<String, String> headers) {
      this.headers = headers;
    }

    public String getPayload() {
      return payload;
    }

    public void setPayload(String payload) {
      this.payload = payload;
    }
  }

  private boolean sockjsEnabled;
  private boolean corsEnabled;
  private List<Handler> handlers;

  public boolean isSockjsEnabled() {
    return sockjsEnabled;
  }

  public void setSockjsEnabled(boolean sockjsEnabled) {
    this.sockjsEnabled = sockjsEnabled;
  }

  public boolean isCorsEnabled() {
    return corsEnabled;
  }

  public void setCorsEnabled(boolean corsEnabled) {
    this.corsEnabled = corsEnabled;
  }

  public List<Handler> getHandlers() {
    return handlers;
  }

  public void setHandlers(
      List<Handler> handlers) {
    this.handlers = handlers;
  }
}
