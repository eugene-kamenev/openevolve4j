package openevolve.service;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import openevolve.Constants;
import openevolve.EvolveSolution;
import openevolve.OpenEvolve;
import openevolve.OpenEvolveConfig;
import openevolve.events.EventListener;
import openevolve.mapelites.MAPElites;
import openevolve.mapelites.Repository.Solution;
import reactor.core.Disposable;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Service
public class OpenEvolveService {

    private final Map<String, Disposable> runningTasks = new ConcurrentHashMap<>();
    private final Map<String, MAPElites<EvolveSolution>> mapElites = new ConcurrentHashMap<>();

    private final EventBus eventService;
    private final RestClient.Builder restBuilder;

    public OpenEvolveService(EventBus eventService, RestClient.Builder restBuilder) {
        this.eventService = eventService;
        this.restBuilder = restBuilder;
    }

    public Mono<Void> create(String id, OpenEvolveConfig config, boolean restart,
            List<EvolveSolution> initialSolutions,
            Map<UUID, Solution<EvolveSolution>> allSolutions) {
        var listener = new EventListener(eventService, id);
        var newMapElites = OpenEvolve.create(config, Constants.OBJECT_MAPPER, restart,
                initialSolutions, allSolutions, restBuilder, List.of(listener));
        newMapElites.addListener(listener);
        mapElites.put(id, newMapElites);
        return startProcess(id,
                Mono.fromRunnable(() -> newMapElites.run(config.mapelites().numIterations()))
                        .subscribeOn(Schedulers.boundedElastic()));
    }

    public <T> Mono<Void> startProcess(String taskId, Mono<T> task) {
        Mono<Void> stopExistingTask = Mono.empty();
        if (runningTasks.containsKey(taskId)) {
            stopExistingTask = stopProcess(taskId);
        }
        return stopExistingTask.then(Mono.defer(() -> {
            runningTasks.put(taskId, task.doFinally(_ -> {
                runningTasks.remove(taskId);
                mapElites.remove(taskId);
            }).subscribe());
            return Mono.empty(); // Task started successfully
        }));
    }

    // Stop a background process
    public Mono<Void> stopProcess(String taskId) {
        Disposable disposable = runningTasks.remove(taskId);
        MAPElites<EvolveSolution> removedMapElites = mapElites.remove(taskId);
        if (disposable != null) {
            disposable.dispose();
            return Mono.empty(); // Task stopped successfully
        }
        return Mono.empty(); // Task not found or already stopped
    }

    // Get status of a task
    public String getStatus(String taskId) {
        if (runningTasks.containsKey(taskId) && !runningTasks.get(taskId).isDisposed()) {
            return "RUNNING";
        }
        return "NOT_RUNNING";
    }

    public Map<String, String> getStatuses() {
        Map<String, String> statuses = new ConcurrentHashMap<>();
        for (String taskId : mapElites.keySet()) {
            if (runningTasks.containsKey(taskId) && !runningTasks.get(taskId).isDisposed()) {
                statuses.put(taskId, "RUNNING");
            } else {
                statuses.put(taskId, "NOT_RUNNING");
            }
        }
        return statuses;
    }
}
