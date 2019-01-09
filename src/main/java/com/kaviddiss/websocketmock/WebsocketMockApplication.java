package com.kaviddiss.websocketmock;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;

@EnableWebSocketMessageBroker
@SpringBootApplication
public class WebsocketMockApplication {

	public static void main(String[] args) {
		SpringApplication.run(WebsocketMockApplication.class, args);
	}

}

