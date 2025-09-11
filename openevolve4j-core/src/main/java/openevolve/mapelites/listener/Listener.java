package openevolve.mapelites.listener;

import java.util.Collection;
import java.util.function.Consumer;

public interface Listener {
	public static <T extends Listener> void callAll(Collection<T> listeners, Consumer<T> consumer) {
		if (listeners != null) {
			for (var listener : listeners) {
				try {
					consumer.accept(listener);
				} catch (Throwable t) {
					System.err.println("Warning: Listener " + listener + " threw exception: " + t.getMessage());
				}
			}
		}
	}
}
