package openevolve.util;

import java.io.*;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.TimeUnit;
import com.fasterxml.jackson.databind.ObjectMapper;
import openevolve.Constants;

public final class BashExecutor {

	private BashExecutor() {}

	public static ExecResult run(String command, Duration timeout, Path workingDir,
			Map<String, String> environment, Charset charset)
			throws IOException, InterruptedException {
		Objects.requireNonNull(command, "command");
		Objects.requireNonNull(timeout, "timeout");
		Objects.requireNonNull(environment, "environment");
		Objects.requireNonNull(charset, "charset");

		List<String> fullCmd = buildShellCommand(command);

		ProcessBuilder pb = new ProcessBuilder(fullCmd);
		if (workingDir != null) {
			pb.directory(workingDir.toFile());
		}
		// Merge/override environment
		if (!environment.isEmpty()) {
			pb.environment().putAll(environment);
		}

		Instant start = Instant.now();
		Process process = pb.start();

		StringBuilder stdout = new StringBuilder();
		StringBuilder stderr = new StringBuilder();

		Thread outThread = gobble(process.getInputStream(), stdout, charset);
		Thread errThread = gobble(process.getErrorStream(), stderr, charset);
		outThread.start();
		errThread.start();

		boolean finished = process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS);
		int exitCode;

		if (!finished) {
			// Timed out: ensure process is definitely killed
			exitCode = ensureProcessKilled(process);
			// Interrupt gobbler threads to unblock them
			outThread.interrupt();
			errThread.interrupt();
		} else {
			exitCode = process.exitValue();
		}

		// Ensure gobblers finish with timeout to prevent indefinite blocking
		long remainingTimeoutMs = Math.max(1000, timeout.toMillis() / 10); // Give threads 10% of
																			// original timeout or 1
																			// second minimum
		outThread.join(remainingTimeoutMs);
		errThread.join(remainingTimeoutMs);

		// Force interrupt if threads are still alive
		if (outThread.isAlive()) {
			outThread.interrupt();
		}
		if (errThread.isAlive()) {
			errThread.interrupt();
		}

		Instant end = Instant.now();

		return new ExecResult(stdout.toString(), stderr.toString(), exitCode,
				Duration.between(start, end), !finished);
	}

	// Executes a script file directly with bash (no -lc), useful for .sh files with shebangs.
	public static ExecResult runScript(Path scriptPath, List<String> args, Duration timeout,
			Path workingDir, Map<String, String> environment, Charset charset)
			throws IOException, InterruptedException {
		Objects.requireNonNull(scriptPath, "scriptPath");
		Objects.requireNonNull(timeout, "timeout");
		Objects.requireNonNull(environment, "environment");
		Objects.requireNonNull(charset, "charset");

		String shell = detectShellExecutable();
		List<String> cmd = new ArrayList<>();
		cmd.add(shell);
		cmd.add(scriptPath.toAbsolutePath().toString());
		if (args != null)
			cmd.addAll(args);

		ProcessBuilder pb = new ProcessBuilder(cmd);
		if (workingDir != null) {
			pb.directory(workingDir.toFile());
		}
		if (!environment.isEmpty()) {
			pb.environment().putAll(environment);
		}

		Instant start = Instant.now();
		Process process = pb.start();

		StringBuilder stdout = new StringBuilder();
		StringBuilder stderr = new StringBuilder();

		Thread outThread = gobble(process.getInputStream(), stdout, charset);
		Thread errThread = gobble(process.getErrorStream(), stderr, charset);
		outThread.start();
		errThread.start();

		boolean finished = process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS);
		int exitCode;

		if (!finished) {
			// Timed out: ensure process is definitely killed
			exitCode = ensureProcessKilled(process);
			// Interrupt gobbler threads to unblock them
			outThread.interrupt();
			errThread.interrupt();
		} else {
			exitCode = process.exitValue();
		}

		// Ensure gobblers finish with timeout
		long remainingTimeoutMs = Math.max(1000, timeout.toMillis() / 10);
		outThread.join(remainingTimeoutMs);
		errThread.join(remainingTimeoutMs);

		// Force interrupt if threads are still alive
		if (outThread.isAlive()) {
			outThread.interrupt();
		}
		if (errThread.isAlive()) {
			errThread.interrupt();
		}

		Instant end = Instant.now();

		return new ExecResult(stdout.toString(), stderr.toString(), exitCode,
				Duration.between(start, end), !finished);
	}

	/**
	 * Ensures a process is definitely killed, with retry logic and verification.
	 *
	 * @param process the process to kill
	 * @return exit code (-1 for timeout)
	 */
	private static int ensureProcessKilled(Process process) {
		ProcessHandle handle = process.toHandle();

		// First, kill all descendants
		handle.descendants().forEach(ph -> {
			try {
				ph.destroyForcibly();
			} catch (Exception ignored) {
			}
		});

		// Then kill the parent itself
		handle.destroyForcibly();

		try {
			if (process.waitFor(2, TimeUnit.SECONDS)) {
				return process.exitValue();
			}
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}

		return -1; // Indicate timeout/forced kill
	}

	private static List<String> buildShellCommand(String command) {
		return Arrays.asList(detectShellExecutable(), "-lc", command);
	}

	private static String detectShellExecutable() {
		return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win") ? "bash"
				: "/bin/bash";
	}

	private static Thread gobble(InputStream stream, StringBuilder sink, Charset charset) {
		return new Thread(() -> {
			try (BufferedReader br = new BufferedReader(new InputStreamReader(stream, charset))) {
				String line;
				while ((line = br.readLine()) != null && !Thread.currentThread().isInterrupted()) {
					sink.append(line).append(System.lineSeparator());
				}
			} catch (IOException ignored) {
				// Swallow to avoid masking the main process result; you may log if desired.
			}
		}, "stream-gobbler-" + UUID.randomUUID());
	}

	public record ExecResult(String stdout, String stderr, int exitCode, Duration duration,
			boolean timedOut) {
		public Map<String, Object> extractMetrics(ObjectMapper mapper,
				Collection<String> metricNames) {
			if (timedOut()) {
				var errorMessage = "Bash script timed out";
				return Map.of(Constants.COMBINED_SCORE, 0.0, "error", errorMessage);
			}
			if (exitCode() != 0) {
				var errorMessage = stderr() != null ? stderr()
						: "Bash script failed with exit code " + exitCode();
				return Map.of(Constants.COMBINED_SCORE, 0.0, "error", errorMessage);
			}
			if (stdout() == null || stdout().isEmpty()) {
				var errorMessage = "Bash script did not produce any output";
				return Map.of(Constants.COMBINED_SCORE, 0.0, "error", errorMessage);
			}
			Map<String, Object> metrics = Util.parseJSON(stdout(), mapper, Constants.MAP_TYPE_REF,
					(m) -> m.keySet().stream().filter(name -> metricNames.contains(name))
							.findFirst().isPresent());
			return metrics != null ? metrics
					: Map.of(Constants.COMBINED_SCORE, 0.0, "error",
							"No valid metrics found in output");
		}
	}
}
