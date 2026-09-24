import dev.escos.fork.ai.DistanceOrder;
import dev.escos.fork.entity.PotentialWork;
import dev.escos.fork.EscoMetrics;
import java.util.*;

/** Diagnostic kernel timing only: not a server throughput or player-capacity benchmark. */
public final class KernelTiming {
    private record Value(int id, double distance) {}
    private static volatile double sink;
    public static void main(String[] args) {
        String kind = args[0]; int size = Integer.parseInt(args[1]);
        int warmup = 1500, samples = 1000;
        Random random = new Random(260207);
        ArrayList<Value> original = new ArrayList<>(), list = new ArrayList<>();
        double[][] points = new double[size][4];
        for (int i = 0; i < size; ++i) {
            Value value = new Value(i, random.nextDouble() * 8192);
            original.add(value); list.add(value);
            points[i] = new double[]{random.nextInt(2048)-1024, random.nextInt(256)-64,
                random.nextInt(2048)-1024, random.nextDouble()};
        }
        PotentialWork potential = new PotentialWork();
        long[] nanos = new long[samples];
        for (int i = -warmup; i < samples; ++i) {
            if (kind.equals("sensor")) for (int j = 0; j < size; ++j) list.set(j, original.get(j));
            long start = System.nanoTime();
            if (kind.equals("sensor")) DistanceOrder.sort(list, Value::distance);
            else sink = potential.sum(size, i % 127, 73, i % 131,
                (index, target, offset) -> System.arraycopy(points[index], 0, target, offset, 4), true);
            long elapsed = System.nanoTime() - start;
            if (i >= 0) nanos[i] = elapsed;
        }
        double mean = Arrays.stream(nanos).average().orElseThrow() / 1000;
        Arrays.sort(nanos);
        System.out.printf(Locale.ROOT, "{\"kind\":\"%s\",\"size\":%d,\"mean_us\":%.4f,\"p50_us\":%.4f,\"p95_us\":%.4f,\"warmup\":%d,\"samples\":%d}%n",
            kind, size, mean, nanos[500]/1000.0, nanos[950]/1000.0, warmup, samples);
        System.out.println(EscoMetrics.snapshot());
    }
}
