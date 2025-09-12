package openevolve.service;

import org.springframework.beans.factory.DisposableBean;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Service;
import com.fasterxml.jackson.databind.ObjectReader;
import com.fasterxml.jackson.databind.ObjectWriter;
import openevolve.Constants;
import openevolve.events.Event;
import openevolve.events.Event.Output;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

@Service
public class EventBus implements DisposableBean {

    public final Sinks.Many<Event<? extends Output>> outputSink =
            Sinks.many().multicast().directBestEffort();
    public final Flux<Event<? extends Output>> outputEventStream = outputSink.asFlux().share();

    private final ObjectReader eventReader;
    private final ObjectWriter eventWriter;

    public EventBus(ApplicationContext context) {
        this.eventReader = Constants.OBJECT_MAPPER.readerFor(Event.class);
        this.eventWriter = Constants.OBJECT_MAPPER.writerFor(Event.class);
    }

    public <T> T read(String event) {
        try {
            return eventReader.readValue(event);
        } catch (Exception e) {
            throw new RuntimeException("Failed to read event", e);
        }
    }

    public String write(Event<?> event) {
        try {
            return eventWriter.writeValueAsString(event);
        } catch (Exception e) {
            throw new RuntimeException("Failed to write event", e);
        }
    }

    public Flux<Event<? extends Output>> outputStream() {
        return outputEventStream;
    }

    public Mono<Void> toOutput(Event<? extends Output> event) {
        // we use tryEmitNext here without checking the result, because if there are no subscribers,
        // we don't want to fail the whole operation, just drop the event
        return Mono.fromRunnable(() -> outputSink.tryEmitNext(event).orThrow()).then();
    }

    @Override
    public void destroy() throws Exception {
        outputSink.tryEmitComplete();
    }
}
