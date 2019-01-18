package com.kaviddiss.websocketmock.template;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class TemplateEngine {

  private static class Token {
    private String value;
    private boolean variable;

    private Token(String value, boolean variable) {
      this.value = value;
      this.variable = variable;
    }
  }

  private static final String VARIABLE_PREFIX = "#\\{";
  private static final String VARIABLE_POSTFIX = "}";

  /**
   *
   * @param template can contain variables: "hello, {user}"
   * @param text template will be matched against this, ie: "hello, world"
   * @param context variables from text will be put into the map, ie: "user" -> "world"
   */
  public void readVariables(String template, String text, Map<String, Object> context) {
    if (template == null || text == null) {
      return;
    }

    List<Token> tokens = tokenize(template);
    int offset = 0;
    for (int i = 0; i < tokens.size(); i++) {
      Token token = tokens.get(i);
      // template and text doesn't match:
      if (!token.variable && text.indexOf(token.value, offset) != offset) {
        break;
      }

      int index = i < tokens.size() - 1 ? text.indexOf(tokens.get(i + 1).value, offset) : text.length();
      int length = token.variable ? index - offset : token.value.length();
      // template and text doesn't match:
      if (length < 0) {
        break;
      }

      if (token.variable) {
        String variable = text.substring(offset, offset + length);
        context.put(token.value, variable);
      }
      offset += length;
    }
  }

  public boolean matches(String template, String text) {
    Map<String, Object> context = new HashMap<>();
    readVariables(template, text, context);
    return !context.isEmpty();
  }

  public String compile(String text, Map<String, Object> context) {
    StringBuilder sb = new StringBuilder();
    List<Token> tokens = tokenize(text);
    for (Token token : tokens) {
      sb.append(token.variable ? context.get(token.value) : token.value);
    }
    return sb.toString();
  }

  private static List<Token> tokenize(String str) {
    List<Token> tokens = new ArrayList<>();
    String[] parts = str.split(VARIABLE_PREFIX);
    for (int i = 0; i < parts.length; i++) {
      if (i == 0) {
        tokens.add(new Token(parts[i], false));
      } else {
        int index = parts[i].indexOf(VARIABLE_POSTFIX);
        String variable = index > -1 ? parts[i].substring(0, index) : parts[i];
        String text = index > -1 ? parts[i].substring(index + 1) : null;
        tokens.add(new Token(variable, true));
        if (!StringUtils.isEmpty(text)) {
          tokens.add(new Token(text, false));
        }
      }
    }
    return tokens;
  }

  public static void main(String[] args) {
    Map<String, Object> context = new HashMap<>();
    TemplateEngine templateEngine = new TemplateEngine();
    templateEngine.readVariables("/topic/user.#{userId}", "/topic/user.47425", context);
//    templateEngine.readVariables("/app/publish/rtc-message-thread.#{threadId}", "/app/user.47425", context);
    System.out.println(templateEngine.compile("/app/user.#{userId}", context));
    System.out.println(templateEngine.compile("{\"type\" : \"join-conference-request\",\"user_id\" : #{userId},\"conference_uuid\" : \"c9372f89-be63-4932-853e-3b46241d061c\",\"message_thread_id\" : 954}", context));
    System.out.println("Matches: " + templateEngine.matches("/topic/user.#{userId}", "/topic/rtc-message-thread.954"));
  }
}
