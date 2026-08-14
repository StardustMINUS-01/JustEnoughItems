package mezz.jei.test.bench;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

/**
 * Runs a benchmark operation on a daemon worker thread with a deadline and a
 * cancellation grace period, mirroring the AE2VM
 * {@code ReferenceCapabilityRunner.invoke} timeout machinery. This keeps a
 * pathological scenario (e.g. unbounded tree expansion) from hanging the test
 * run: if the worker does not stop within the grace window the outcome is
 * marked non-cooperative instead of failing the suite.
 */
public final class JeiBenchRunner {
	public static final Duration DEFAULT_DEADLINE = Duration.ofSeconds(5);
	public static final Duration DEFAULT_GRACE = Duration.ofMillis(500);

	private JeiBenchRunner() {
	}

	public static <T> Outcome<T> invoke(Supplier<T> operation) {
		return invoke(operation, DEFAULT_DEADLINE, DEFAULT_GRACE);
	}

	public static <T> Outcome<T> invoke(
		Supplier<T> operation,
		Duration deadline,
		Duration grace
	) {
		long started = System.nanoTime();
		AtomicReference<Thread> workerRef = new AtomicReference<>();
		CompletableFuture<T> future = new CompletableFuture<>();
		Thread worker = new Thread(() -> {
			workerRef.set(Thread.currentThread());
			try {
				future.complete(operation.get());
			} catch (Throwable failure) {
				future.completeExceptionally(failure);
			}
		});
		worker.setDaemon(true);
		worker.setName("jei-bench-worker");
		worker.start();
		try {
			T value = future.get(deadline.toNanos(), TimeUnit.NANOSECONDS);
			return new Outcome<>(value, System.nanoTime() - started, null, false, false);
		} catch (TimeoutException timeout) {
			worker.interrupt();
			try {
				long graceNanos = grace.toNanos();
				worker.join(graceNanos / 1_000_000L, (int) (graceNanos % 1_000_000L));
			} catch (InterruptedException interrupted) {
				Thread.currentThread().interrupt();
				return new Outcome<>(null, System.nanoTime() - started, interrupted, true, true);
			}
			return new Outcome<>(null, System.nanoTime() - started, timeout, true, worker.isAlive());
		} catch (InterruptedException interrupted) {
			Thread.currentThread().interrupt();
			return new Outcome<>(null, System.nanoTime() - started, interrupted, false, false);
		} catch (ExecutionException failed) {
			return new Outcome<>(null, System.nanoTime() - started, failed.getCause(), false, false);
		}
	}

	/**
	 * The outcome of a single benchmark run.
	 *
	 * @param value           the operation result (null on failure/timeout)
	 * @param elapsedNanos    wall-clock time spent on the worker thread
	 * @param failure         the failure cause (null when completed successfully)
	 * @param timedOut        true when the deadline expired
	 * @param nonCooperative  when timed out: true if the worker ignored the interrupt
	 */
	public record Outcome<T>(
		T value,
		long elapsedNanos,
		Throwable failure,
		boolean timedOut,
		boolean nonCooperative
	) {
		public boolean completed() {
			return !timedOut && failure == null;
		}

		public double elapsedMs() {
			return elapsedNanos / 1_000_000.0;
		}
	}
}
