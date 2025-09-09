package openevolve.service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;
import openevolve.Constants;
import openevolve.EvolveSolution;
import openevolve.OpenEvolve;
import openevolve.OpenEvolveConfig;
import openevolve.events.EventListener;
import openevolve.mapelites.MAPElites;
import reactor.core.Disposable;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Service
public class OpenEvolveService {

    private final Map<String, Disposable> runningTasks = new ConcurrentHashMap<>();
    private final Map<String, MAPElites<EvolveSolution>> mapElites = new ConcurrentHashMap<>();

    private final EventService eventService;

    public OpenEvolveService(EventService eventService) {
        this.eventService = eventService;
    }

    public Mono<Void> create(String id, OpenEvolveConfig config) {
        var newMapElites = OpenEvolve.create(config, Constants.OBJECT_MAPPER);
        newMapElites.addListener(new EventListener(eventService, id));
        mapElites.put(id, newMapElites);
        return Mono.fromRunnable(() -> newMapElites.run(config.mapelites().numIterations()))
                .subscribeOn(Schedulers.boundedElastic()).then();
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
    public Mono<String> getStatus(String taskId) {
        if (runningTasks.containsKey(taskId) && !runningTasks.get(taskId).isDisposed()) {
            return Mono.just("RUNNING");
        }
        return Mono.just("NOT_RUNNING");
    }


}
