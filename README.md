This is a mock websocket server similar to WireMock to simulate responses for testing purposes.

See below command line to run it:
```
mvn spring-boot:run -Dserver.port=8080 -Dspring.config.location=file:/Users/me/mock-websocket-application.yml
``` 

where ```mock-websocket-application.yml``` is an external application.yml file containing the request/response samples.

Sample configuration:
```yaml
mock:
  sockjs-enabled: false
  cors-enabled: true
  handlers:
  - request:
      command: CONNECT
    response:
      command: CONNECTED
      headers:
        server: RabbitMQ/3.6.5
        heart-beat: 30000,30000
        version: 1.1
        user-name: User [id=46960]
  - request:
      command: SUBSCRIBE
      headers:
        destination: '/topic/user.{userId}'
    response:
      command: MESSAGE
      headers:
        destination: /topic/user.#{userId}
        content-type: application/json;charset=UTF-8
      payload: '{"msg" : "hello"}'
  - request:
      command: SEND
      headers:
        destination: /app/publish/message-thread.#{threadId}
        content-length: 16
      payload: '{"type":"ready"}'
    responses:
    - command: MESSAGE
      headers:
        destination: /topic/message-thread.#{threadId}
        message-id: T_sub-0@@session-bHups5kzrjprak8H6cxFUw@@1
        content-type: application/json;charset=UTF-8
        content-length: 16
      payload: '{"type":"ready"}'
    - command: MESSAGE
      headers:
        message-id: T_sub-1@@session-bHups5kzrjprak8H6cxFUw@@2
        content-length: 15
      payload: '{"type":"done"}'

```

As you can see above, you can also define variables (#{userId} and #{threadId}) in the request and use them in response headers or payload.  