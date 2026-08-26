package com.julflips.nerv_printer.dev;

import java.io.IOException;
import java.lang.instrument.ClassDefinition;
import java.lang.instrument.Instrumentation;
import java.lang.instrument.UnmodifiableClassException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * Watches the remapped Nerv Printer jar and injects rebuilt classes into the
 * running Minecraft JVM. JetBrains Runtime's enhanced class redefinition must
 * be enabled so method and field shape changes are accepted.
 */
public final class NervLiveReloadAgent {
    private static final String CLASS_PREFIX = "com.julflips.nerv_printer.";
    private static final long POLL_MILLIS = 500L;
    private static final long STABLE_MILLIS = 750L;
    private static final DateTimeFormatter CLOCK =
        DateTimeFormatter.ofPattern("HH:mm:ss");

    private NervLiveReloadAgent() {
    }

    public static void premain(
        String agentArguments,
        Instrumentation instrumentation
    ) {
        System.setProperty("nerv.liveReload.active", "true");
        if (!instrumentation.isRedefineClassesSupported()) {
            log("ERROR: this JVM does not support class redefinition");
            return;
        }
        if (agentArguments == null || agentArguments.isBlank()) {
            log("ERROR: no remapped jar path was supplied to -javaagent");
            return;
        }

        Path watchedJar = Path.of(agentArguments).toAbsolutePath().normalize();
        log("watching remapped mod jar " + watchedJar);
        Thread watcher = Thread.ofPlatform()
            .name("Nerv-Live-Reload")
            .daemon(true)
            .unstarted(() -> watch(watchedJar, instrumentation));
        watcher.setUncaughtExceptionHandler((thread, failure) ->
            log("ERROR: watcher stopped: " + failure));
        watcher.start();
    }

    private static void watch(
        Path watchedJar,
        Instrumentation instrumentation
    ) {
        Stamp acknowledged = stamp(watchedJar);
        Map<String, byte[]> acceptedClassBytes;
        try {
            acceptedClassBytes = readClassBytes(watchedJar);
            log("captured baseline for " + acceptedClassBytes.size()
                + " Nerv classes; unchanged classes will never be redefined");
        } catch (IOException failure) {
            log("ERROR: could not capture the initial class baseline: " + failure);
            return;
        }
        while (!Thread.currentThread().isInterrupted()) {
            sleep(POLL_MILLIS);
            Stamp observed = stamp(watchedJar);
            if (!observed.valid() || observed.equals(acknowledged)) continue;

            Stamp stable = waitUntilStable(watchedJar, observed);
            if (!stable.valid() || stable.equals(acknowledged)) continue;
            try {
                ReloadResult result = redefine(
                    watchedJar,
                    instrumentation,
                    acceptedClassBytes
                );
                acceptedClassBytes = result.acceptedClassBytes();
                acknowledged = stable;
                log("injected build: " + result.changed()
                    + " classes changed, " + result.redefined()
                    + " loaded classes redefined, " + result.unavailable()
                    + " changed classes not loaded yet, " + result.skipped()
                    + " generated/unsafe classes intentionally skipped");
            } catch (Throwable failure) {
                // Do not acknowledge a failed build. The next poll retries it,
                // which covers transient jar replacement and class-load races.
                log("ERROR: injection failed and will retry: " + failure);
                sleep(1_000L);
            }
        }
    }

    private static ReloadResult redefine(
        Path watchedJar,
        Instrumentation instrumentation,
        Map<String, byte[]> acceptedClassBytes
    ) throws IOException, ClassNotFoundException, UnmodifiableClassException {
        Map<String, Class<?>> loaded = new HashMap<>();
        for (Class<?> type : instrumentation.getAllLoadedClasses()) {
            if (type.getName().startsWith(CLASS_PREFIX)
                && instrumentation.isModifiableClass(type)) {
                loaded.put(type.getName(), type);
            }
        }

        Map<String, byte[]> currentClassBytes = readClassBytes(watchedJar);
        ArrayList<ClassDefinition> definitions = new ArrayList<>();
        int changedClasses = 0;
        int buildOnlyClasses = 0;
        int skippedClasses = 0;
        for (Map.Entry<String, byte[]> entry : currentClassBytes.entrySet()) {
            byte[] previous = acceptedClassBytes.get(entry.getKey());
            if (previous != null && Arrays.equals(previous, entry.getValue())) {
                continue;
            }
            changedClasses++;
            if (!isEligibleForLiveRedefinition(entry.getKey())) {
                skippedClasses++;
                continue;
            }
            Class<?> loadedClass = loaded.get(entry.getKey());
            if (loadedClass == null) {
                buildOnlyClasses++;
                continue;
            }
            definitions.add(new ClassDefinition(loadedClass, entry.getValue()));
        }

        if (!definitions.isEmpty()) {
            instrumentation.redefineClasses(
                definitions.toArray(ClassDefinition[]::new)
            );
        }
        return new ReloadResult(
            changedClasses,
            definitions.size(),
            buildOnlyClasses,
            skippedClasses,
            currentClassBytes
        );
    }

    private static boolean isEligibleForLiveRedefinition(String className) {
        // javac rewrites dozens of StaircasedPrinter nested enum/record class
        // files when only the outer source line table changes. Redefining those
        // unchanged runtime shapes caused unnecessary DCEVM/Fabric linkage
        // risk. Runtime fixes in this project live in the outer controller or
        // top-level Raster helpers; nested/mixin/interface shape changes still
        // require a conventional restart.
        if (className.indexOf('$') >= 0) return false;
        return className.equals(
            "com.julflips.nerv_printer.modules.StaircasedPrinter"
        ) || className.startsWith(
            "com.julflips.nerv_printer.utils.Raster"
        ) || className.equals(
            "com.julflips.nerv_printer.utils.BoatFlyAdapter"
        ) || className.equals(
            "com.julflips.nerv_printer.utils.BoatRasterCheckpointStore"
        ) || className.equals(
            "com.julflips.nerv_printer.utils.AdaptiveReachEnvelope"
        ) || className.equals(
            "com.julflips.nerv_printer.utils.BuildHeightSelector"
        );
    }

    private static Map<String, byte[]> readClassBytes(Path watchedJar)
        throws IOException {
        LinkedHashMap<String, byte[]> classBytes = new LinkedHashMap<>();
        try (JarFile jar = new JarFile(watchedJar.toFile(), false)) {
            List<JarEntry> entries = jar.stream()
                .filter(entry -> !entry.isDirectory())
                .filter(entry -> entry.getName().endsWith(".class"))
                .filter(entry -> entry.getName().startsWith(
                    CLASS_PREFIX.replace('.', '/')
                ))
                .sorted(Comparator.comparing(JarEntry::getName))
                .toList();
            for (JarEntry entry : entries) {
                String className = entry.getName()
                    .substring(0, entry.getName().length() - 6)
                    .replace('/', '.');
                try (var input = jar.getInputStream(entry)) {
                    classBytes.put(className, input.readAllBytes());
                }
            }
        }
        return Map.copyOf(classBytes);
    }

    private static Stamp waitUntilStable(Path watchedJar, Stamp first) {
        Stamp previous = first;
        while (true) {
            sleep(STABLE_MILLIS);
            Stamp current = stamp(watchedJar);
            if (current.equals(previous)) return current;
            if (!current.valid()) return current;
            previous = current;
        }
    }

    private static Stamp stamp(Path path) {
        try {
            if (!Files.isRegularFile(path)) return Stamp.INVALID;
            FileTime modified = Files.getLastModifiedTime(path);
            return new Stamp(modified.toMillis(), Files.size(path));
        } catch (IOException ignored) {
            return Stamp.INVALID;
        }
    }

    private static void sleep(long milliseconds) {
        try {
            Thread.sleep(milliseconds);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    private static void log(String message) {
        System.out.println("[" + LocalTime.now().format(CLOCK)
            + "] [NervLiveReload] " + message);
    }

    private record Stamp(long modifiedMillis, long size) {
        private static final Stamp INVALID = new Stamp(-1L, -1L);

        private boolean valid() {
            return modifiedMillis >= 0L && size > 0L;
        }
    }

    private record ReloadResult(
        int changed,
        int redefined,
        int unavailable,
        int skipped,
        Map<String, byte[]> acceptedClassBytes
    ) {
    }
}
