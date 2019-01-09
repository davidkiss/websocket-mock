package com.kaviddiss.websocketmock.config;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.StompWebSocketEndpointRegistration;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {
  @Autowired
  private MockWebSocketProperties properties;

  @Override
  public void registerStompEndpoints(StompEndpointRegistry registry) {
    StompWebSocketEndpointRegistration registration = registry.addEndpoint("/");

    if (properties.isCorsEnabled()) {
      registration = registration.setAllowedOrigins("*");
    }
    if (properties.isSockjsEnabled()) {
      registration.withSockJS();
    }
  }

  @Bean
  public ExecutorService executorService() {
    return Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors() * 2);
  }
}
