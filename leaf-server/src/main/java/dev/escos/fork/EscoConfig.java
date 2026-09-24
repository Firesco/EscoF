package dev.escos.fork;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/** Startup-only configuration. No plugin APIs or world state are accessed here. */
public final class EscoConfig {
    private static final Properties VALUES = load();
    public static final int WORKERS = workers();
    public static final boolean CALLER_COMPUTE = bool("compute.caller-participates", true);
    public static final boolean SORT_PIPELINE = bool("sensors.pipeline", true);
    public static final boolean ACTIVATION = bool("activation.enabled", true);
    public static final int ACTIVATION_MIN_PLAYERS = integer("activation.min-players", 8, 1, 10000);
    public static final int ACTIVATION_MIN_ENTITIES = integer("activation.min-entities", 1024, 1, 10000000);
    public static final boolean VERIFY_ACTIVATION = bool("activation.verify", false);
    public static final boolean SENSORS = bool("sensors.enabled", true);
    public static final int SORT_PARALLEL_THRESHOLD = integer("sensors.parallel-threshold", 65536, 256, 10000000);
    public static final boolean POI_EMPTY_SEARCH = bool("poi.loaded-empty-check", true);
    public static final boolean LINEAR_PATHS = bool("paths.linear-reconstruction", true);
    public static final boolean POTENTIAL = bool("spawning.parallel-potential", true);
    public static final boolean POTENTIAL_SOA = bool("spawning.separate-arrays", true);
    public static final int POTENTIAL_GRAIN = integer("spawning.items-per-task", 16384, 256, 10000000);
    public static final int POTENTIAL_THRESHOLD = integer("spawning.parallel-threshold", 32768, 256, 10000000);

    private EscoConfig() {}

    private static Properties load() {
        final Path currentPath = Path.of("config", "escof.properties");
        final Path legacyPath = Path.of("config", "escos.properties");
        final Path path = !Files.exists(currentPath) && Files.exists(legacyPath) ? legacyPath : currentPath;
        final Properties values = new Properties();
        values.setProperty("workers", "auto");
        values.setProperty("compute.caller-participates", "true");
        values.setProperty("sensors.pipeline", "true");
        values.setProperty("activation.enabled", "true");
        values.setProperty("activation.min-players", "8");
        values.setProperty("activation.min-entities", "1024");
        values.setProperty("activation.verify", "false");
        values.setProperty("sensors.enabled", "true");
        values.setProperty("sensors.parallel-threshold", "65536");
        values.setProperty("poi.loaded-empty-check", "true");
        values.setProperty("paths.linear-reconstruction", "true");
        values.setProperty("spawning.parallel-potential", "true");
        values.setProperty("spawning.separate-arrays", "true");
        values.setProperty("spawning.items-per-task", "16384");
        values.setProperty("spawning.parallel-threshold", "32768");
        try {
            if (Files.exists(path)) {
                try (InputStream input = Files.newInputStream(path)) { values.load(input); }
            } else {
                Files.createDirectories(path.getParent());
                try (OutputStream output = Files.newOutputStream(path)) {
                    values.store(output, "EscoF 0.7.0 / Minecraft 26.2. Developed By Firesco. Restart after editing. workers: auto (-1), all, 0 serial, 1..32767. auto leaves two processors for other work. Worker count is an upper bound, not a speed guarantee. activation.verify compares with the upstream rules.");
                }
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to read/write " + path, exception);
        }
        return values;
    }

    private static String value(final String key, final String fallback) {
        return System.getProperty("escof." + key,
            System.getProperty("escos." + key, VALUES.getProperty(key, fallback))).trim();
    }

    private static boolean bool(final String key, final boolean fallback) {
        final String value = value(key, Boolean.toString(fallback));
        if (!value.equalsIgnoreCase("true") && !value.equalsIgnoreCase("false")) {
            throw new IllegalArgumentException("escof." + key + " must be true or false");
        }
        return Boolean.parseBoolean(value);
    }

    private static int integer(final String key, final int fallback, final int min, final int max) {
        final int number = Integer.parseInt(value(key, Integer.toString(fallback)));
        if (number < min || number > max) {
            throw new IllegalArgumentException("escof." + key + " must be in " + min + ".." + max);
        }
        return number;
    }

    private static int workers() {
        final String configured = value("workers", "auto");
        final int processors = Runtime.getRuntime().availableProcessors();
        if (configured.equalsIgnoreCase("all")) return Math.min(32767, processors);
        if (configured.equalsIgnoreCase("auto") || configured.equals("-1")) return Math.max(0, Math.min(32767, processors - 2));
        return integer("workers", 0, 0, 32767);
    }
}
