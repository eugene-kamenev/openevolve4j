package openevolve.web;

import java.util.HashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.http.codec.ServerCodecConfigurer;
import org.springframework.web.reactive.HandlerMapping;
import org.springframework.web.reactive.config.CorsRegistry;
import org.springframework.web.reactive.config.EnableWebFlux;
import org.springframework.web.reactive.config.WebFluxConfigurer;
import org.springframework.web.reactive.handler.SimpleUrlHandlerMapping;
import org.springframework.web.reactive.socket.WebSocketHandler;
import org.springframework.web.reactive.socket.server.support.WebSocketHandlerAdapter;
import com.fasterxml.jackson.databind.ObjectMapper;

@EnableWebFlux
@Configuration
public class WsConfig implements WebFluxConfigurer {

    @Autowired
    ObjectMapper objectMapper;

    @Bean
    public HandlerMapping handlerMapping(WsHandler wsHandler) {
        Map<String, WebSocketHandler> map = new HashMap<>();
        map.put("/ws", wsHandler);
        var mapping = new SimpleUrlHandlerMapping();
        mapping.setUrlMap(map);
        mapping.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return mapping;
    }

    @Bean
    public WebSocketHandlerAdapter handlerAdapter() {
        return new WebSocketHandlerAdapter();
    }

    @Override
	public void configureHttpMessageCodecs(ServerCodecConfigurer configurer) {
		var defaults = configurer.defaultCodecs();
        defaults.jackson2JsonEncoder(
                    new org.springframework.http.codec.json.Jackson2JsonEncoder(objectMapper));
            defaults.jackson2JsonDecoder(
                    new org.springframework.http.codec.json.Jackson2JsonDecoder(objectMapper));
	}

    @Override
    public void addCorsMappings(CorsRegistry corsRegistry) {
        corsRegistry.addMapping("/**").allowedOrigins("*").allowedMethods("*").maxAge(3600);
    }
}
