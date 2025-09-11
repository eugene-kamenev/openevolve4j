package openevolve.web;

import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.socket.WebSocketHandler;
import org.springframework.web.reactive.socket.WebSocketMessage;
import org.springframework.web.reactive.socket.WebSocketSession;
import openevolve.events.Event;
import openevolve.events.Event.Input;
import openevolve.service.ConfigService;
import openevolve.service.EventBus;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
public class WsHandler implements WebSocketHandler {

	private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(WsHandler.class);

	private final EventBus eventService;
	private final ConfigService configService;
	private final ApplicationContext context;

	public WsHandler(EventBus eventService, ConfigService configService, ApplicationContext context) {
		this.eventService = eventService;
		this.configService = configService;
		this.context = context;
	}

	@Override
	public Mono<Void> handle(WebSocketSession session) {
		var input = session.receive()
				.map(WebSocketMessage::getPayloadAsText)
				.map(this::readInput)
				.flatMap(event -> event.payload().get().map(p -> new Event<>(event.id(), p)))
				.contextWrite(ctx -> ctx.put(Event.Input.APP_CTX_KEY, context));
		return session.send(Flux.merge(input, eventService.outputStream())
				.map(event -> session.textMessage(eventService.write(event))))
				.doFinally(s -> log.info("WebSocket session {} closed with signal {}", session.getId(), s));
	}

	private Event<? extends Input<?>> readInput(String payload) {
		return eventService.read(payload);
	}
}
