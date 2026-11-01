import java.util.Arrays;
import java.util.Locale;
import java.util.stream.IntStream;

/**
 * Optional standalone microprobe: math-only primitive arrays, no Minecraft world,
 * collision, expression RNG or event scheduling. Does NOT prove parallel client safety.
 * Run with: java tools/ParallelKernelProbe.java
 */
public class ParallelKernelProbe {
    private static volatile double sink;
    private static final int[] COUNTS = {1_000, 10_000, 100_000, 1_000_000};
    private static final int WARMUPS = 3;
    private static final int TRIALS = 7;
    private static final int STEPS = 24;
    private static final int CHUNK = 4_096;
    private static final double DRAG = Math.exp(-0.4 / 20.0);

    private static void step(double[] x, double[] velocity, boolean parallel) {
        if (parallel) {
            int chunks = (x.length + CHUNK - 1) / CHUNK;
            IntStream.range(0, chunks).parallel().forEach(chunk -> {
                int end = Math.min(x.length, (chunk + 1) * CHUNK);
                for (int i = chunk * CHUNK; i < end; i++) {
                    double v = (velocity[i] - 0.00025) * DRAG;
                    velocity[i] = v;
                    x[i] += v;
                }
            });
        } else {
            for (int i = 0; i < x.length; i++) {
                double v = (velocity[i] - 0.00025) * DRAG;
                velocity[i] = v;
                x[i] += v;
            }
        }
    }

    private static double trial(int count, boolean parallel) {
        double[] x = new double[count];
        double[] velocity = new double[count];
        for (int i = 0; i < count; i++) {
            x[i] = i * 0.000001;
            velocity[i] = i * 0.0000002;
        }
        long start = System.nanoTime();
        for (int step = 0; step < STEPS; step++) {
            step(x, velocity, parallel);
        }
        long elapsed = System.nanoTime() - start;
        sink = x[count - 1] + velocity[count - 1];
        return elapsed / STEPS / 1_000_000.0;
    }

    private static double median(double[] values) {
        Arrays.sort(values);
        return values[values.length / 2];
    }

    public static void main(String[] args) {
        System.out.printf("Java=%s processors=%d commonPool=%d%n",
                Runtime.version(), Runtime.getRuntime().availableProcessors(),
                java.util.concurrent.ForkJoinPool.getCommonPoolParallelism());
        System.out.println("Primitive math-only positions/velocity, 24 steps each (no world/events/RNG):");
        for (int count : COUNTS) {
            for (int i = 0; i < WARMUPS; i++) {
                trial(count, false);
                trial(count, true);
            }
            double[] serial = new double[TRIALS];
            double[] parallel = new double[TRIALS];
            for (int i = 0; i < TRIALS; i++) {
                serial[i] = trial(count, false);
                parallel[i] = trial(count, true);
            }
            double a = median(serial);
            double b = median(parallel);
            System.out.printf(Locale.ROOT, "%7d: serial %.5f ms/tick, parallel %.5f ms/tick, ratio %.2fx%n",
                    count, a, b, a / b);
        }
        System.out.println("sink=" + sink);
    }
}
