package com.grunka.random.fortuna;

import com.grunka.random.fortuna.accumulator.Accumulator;
import com.grunka.random.fortuna.entropy.*;

import java.io.IOException;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Fortuna extends Random {
    private static final int MIN_POOL_SIZE = 64;
    private static final int[] POWERS_OF_TWO = initializePowersOfTwo();

    private static int[] initializePowersOfTwo() {
        int[] result = new int[32];
        for (int power = 0; power < result.length; power++) {
            result[power] = (int) StrictMath.pow(2, power);
        }
        return result;
    }

    private long lastReseedTime = 0;
    private long reseedCount = 0;
    private final RandomDataBuffer randomDataBuffer;
    private final Generator generator;
    private final Pool[] pools;
    private final Accumulator accumulator;

    public static Fortuna createInstance() {
        Pool[] pools = new Pool[32];
        for (int pool = 0; pool < pools.length; pool++) {
            pools[pool] = new Pool();
        }
        Accumulator accumulator = new Accumulator(pools);
        accumulator.addSource(new SchedulingEntropySource());
        accumulator.addSource(new GarbageCollectorEntropySource());
        accumulator.addSource(new LoadAverageEntropySource());
        accumulator.addSource(new FreeMemoryEntropySource());
        accumulator.addSource(new ThreadTimeEntropySource());
        accumulator.addSource(new UptimeEntropySource());
        if (Files.exists(Paths.get("/dev/urandom"))) {
            accumulator.addSource(new URandomEntropySource());
        }
        while (pools[0].size() < MIN_POOL_SIZE) {
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                throw new Error("Interrupted while waiting for initialization", e);
            }
        }
        return new Fortuna(new Generator(), new RandomDataBuffer(), pools, accumulator);
    }

    private Fortuna(Generator generator, RandomDataBuffer randomDataBuffer, Pool[] pools, Accumulator accumulator) {
        this.generator = generator;
        this.randomDataBuffer = randomDataBuffer;
        this.pools = pools;
        this.accumulator = accumulator;
    }

    private byte[] randomData(int bytes) {
        long now = System.currentTimeMillis();
        if (pools[0].size() >= MIN_POOL_SIZE && now - lastReseedTime > 100) {
            lastReseedTime = now;
            reseedCount++;
            byte[] seed = new byte[pools.length * 32]; // Maximum potential length
            int seedLength = 0;
            for (int pool = 0; pool < pools.length; pool++) {
                if (reseedCount % POWERS_OF_TWO[pool] == 0) {
                    System.arraycopy(pools[pool].getAndClear(), 0, seed, seedLength, 32);
                    seedLength += 32;
                }
            }
            generator.reseed(Arrays.copyOf(seed, seedLength));
        }
        if (reseedCount == 0) {
            throw new IllegalStateException("Generator not reseeded yet");
        } else {
            return generator.pseudoRandomData(bytes);
        }
    }

    @Override
    protected int next(int bits) {
        return randomDataBuffer.next(bits, () -> randomData(1024 * 1024));
    }

    @Override
    public synchronized void setSeed(long seed) {
        // Does not do anything
    }

    public static void main(String[] args) throws IOException {
        boolean hasLimit = false;
        BigInteger limit = BigInteger.ZERO;
        if (args.length == 1) {
            String amount = args[0];
            Matcher matcher = Pattern.compile("^([1-9][0-9]*)(K|M|G)?$").matcher(amount);
            if (matcher.matches()) {
                String number = matcher.group(1);
                String suffix = matcher.group(2);
                limit = new BigInteger(number);
                if (suffix != null) {
                    if ("K".equals(suffix)) {
                        limit = limit.multiply(BigInteger.valueOf(1024));
                    } else if ("M".equals(suffix)) {
                        limit = limit.multiply(BigInteger.valueOf(1024 * 1024));
                    } else if ("G".equals(suffix)) {
                        limit = limit.multiply(BigInteger.valueOf(1024 * 1024 * 1024));
                    } else {
                        System.err.println("Unrecognized suffix");
                        System.exit(1);
                    }
                }
                hasLimit = true;
            } else {
                System.err.println("Unrecognized amount " + amount);
                System.exit(1);
            }
        } else if (args.length > 1) {
            System.err.println("Unrecognized parameters " + Arrays.toString(args));
            System.exit(1);
        }
        Fortuna fortuna = Fortuna.createInstance();
        final BigInteger fourK = BigInteger.valueOf(4 * 1024);
        while (!hasLimit || limit.compareTo(BigInteger.ZERO) > 0) {
            final byte[] buffer;
            if (hasLimit) {
                if (fourK.compareTo(limit) < 0) {
                    buffer = new byte[4 * 1024];
                    limit = limit.subtract(fourK);
                } else {
                    buffer = new byte[limit.intValue()];
                    limit = BigInteger.ZERO;
                }
            } else {
                buffer = new byte[4 * 1024];
            }
            fortuna.nextBytes(buffer);
            System.out.write(buffer);
        }
    }


    public void shutdown(long timeout, TimeUnit unit) throws InterruptedException {
        accumulator.shutdown(timeout, unit);
    }

    public void shutdown() throws InterruptedException {
        shutdown(30, TimeUnit.SECONDS);
    }
}
