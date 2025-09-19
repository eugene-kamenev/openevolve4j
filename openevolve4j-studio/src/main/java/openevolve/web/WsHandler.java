package openevolve.web;

import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.socket.WebSocketHandler;
import org.springframework.web.reactive.socket.WebSocketSession;
import openevolve.events.Event;
import openevolve.events.Event.Input;
import openevolve.service.EventBus;
import reactor.core.publisher.Mono;

@Service
public class WsHandler implements WebSocketHandler {

	private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(WsHandler.class);

	private final EventBus eventService;
	private final ApplicationContext context;

	public WsHandler(EventBus eventService, ApplicationContext context) {
		this.eventService = eventService;
		this.context = context;
	}

	@Override
	public Mono<Void> handle(WebSocketSession session) {
		return session.send(eventService.outputStream()
				.map(event -> session.textMessage(eventService.write(event)))
				.doFinally(s -> log.info("WebSocket session {} closed with signal {}", session.getId(), s)));
	}

	private Event<? extends Input<?>> readInput(String payload) {
		return eventService.read(payload);
	}
}
