package openevolve.service;

import java.util.HashMap;
import org.springframework.stereotype.Service;
import openevolve.events.Event.Connected;

@Service
public class CommandService {
	
	private final OpenEvolveService openEvolveService;
	private final ConfigService configService;

	public CommandService(OpenEvolveService openEvolveService, ConfigService configService) {
		this.openEvolveService = openEvolveService;
		this.configService = configService;
	}

	public Connected connect() {
		var configs = configService.reload();
		var statuses = new HashMap<String, String>();
		configs.keySet().stream().forEach(
			key -> statuses.put(key, openEvolveService.getStatus(key))
		);
		return new Connected(configs, statuses);
	}

}
