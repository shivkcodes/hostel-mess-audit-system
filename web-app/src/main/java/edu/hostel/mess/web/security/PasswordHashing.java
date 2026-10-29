package edu.hostel.mess.web.security;

import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

import org.bouncycastle.crypto.generators.Argon2BytesGenerator;
import org.bouncycastle.crypto.params.Argon2Parameters;

/** Argon2id password encoding via the vetted Bouncy Castle Java implementation. */
public final class PasswordHashing {
    public static final int MEMORY_KIB = 19_456;
    public static final int ITERATIONS = 2;
    public static final int PARALLELISM = 1;
    public static final int MIN_PASSWORD_LENGTH = 12;
    public static final int MAX_PASSWORD_LENGTH = 128;
    private static final int SALT_LENGTH = 16;
    private static final int HASH_LENGTH = 32;
    private static final int ARGON2_VERSION = Argon2Parameters.ARGON2_VERSION_13;
    private static final String ALGORITHM = "argon2id";
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final byte[] DUMMY_SALT = new byte[] {
            0x48, 0x6f, 0x73, 0x74, 0x65, 0x6c, 0x2d, 0x6d, 0x65, 0x73, 0x73, 0x2d, 0x64, 0x75, 0x6d, 0x6d
    };

    private PasswordHashing() { }

    public static String hash(char[] password) {
        validatePassword(password);
        byte[] salt = new byte[SALT_LENGTH];
        RANDOM.nextBytes(salt);
        byte[] derived = null;
        try {
            derived = derive(password, salt, MEMORY_KIB, ITERATIONS, PARALLELISM);
            Base64.Encoder encoder = Base64.getEncoder().withoutPadding();
            return ALGORITHM + "$v=19$m=" + MEMORY_KIB + ",t=" + ITERATIONS + ",p=" + PARALLELISM
                    + "$" + encoder.encodeToString(salt) + "$" + encoder.encodeToString(derived);
        } finally {
            Arrays.fill(salt, (byte) 0);
            if (derived != null) {
                Arrays.fill(derived, (byte) 0);
            }
        }
    }

    public static boolean verify(char[] password, String encodedHash) {
        if (password == null || password.length == 0 || password.length > MAX_PASSWORD_LENGTH
                || encodedHash == null) {
            performDummyWork(password);
            return false;
        }
        try {
            String[] parts = encodedHash.split("\\$", -1);
            if (parts.length != 5 || !ALGORITHM.equals(parts[0]) || !"v=19".equals(parts[1])) {
                performDummyWork(password);
                return false;
            }
            HashParameters parameters = parseParameters(parts[2]);
            if (parameters == null) {
                performDummyWork(password);
                return false;
            }
            Base64.Decoder decoder = Base64.getDecoder();
            byte[] salt = decoder.decode(parts[3]);
            byte[] expected = decoder.decode(parts[4]);
            if (salt.length < SALT_LENGTH || salt.length > 64 || expected.length != HASH_LENGTH) {
                Arrays.fill(salt, (byte) 0);
                Arrays.fill(expected, (byte) 0);
                performDummyWork(password);
                return false;
            }
            byte[] actual = derive(password, salt, parameters.memoryKib(),
                    parameters.iterations(), parameters.parallelism());
            try {
                return MessageDigest.isEqual(expected, actual);
            } finally {
                Arrays.fill(salt, (byte) 0);
                Arrays.fill(expected, (byte) 0);
                Arrays.fill(actual, (byte) 0);
            }
        } catch (IllegalArgumentException exception) {
            performDummyWork(password);
            return false;
        }
    }

    public static void performDummyWork(char[] password) {
        char[] workPassword = password == null || password.length == 0
                || password.length > MAX_PASSWORD_LENGTH ? new char[MIN_PASSWORD_LENGTH] : password;
        byte[] result = null;
        try {
            result = derive(workPassword, DUMMY_SALT, MEMORY_KIB, ITERATIONS, PARALLELISM);
        } finally {
            if (result != null) {
                Arrays.fill(result, (byte) 0);
            }
            if (workPassword != password) {
                Arrays.fill(workPassword, '\0');
            }
        }
    }

    private static HashParameters parseParameters(String encoded) {
        String[] values = encoded.split(",", -1);
        if (values.length != 3 || !values[0].startsWith("m=")
                || !values[1].startsWith("t=") || !values[2].startsWith("p=")) {
            return null;
        }
        try {
            int memoryKib = Integer.parseInt(values[0].substring(2));
            int iterations = Integer.parseInt(values[1].substring(2));
            int parallelism = Integer.parseInt(values[2].substring(2));
            if (memoryKib != MEMORY_KIB || iterations != ITERATIONS || parallelism != PARALLELISM) {
                return null;
            }
            return new HashParameters(memoryKib, iterations, parallelism);
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private static byte[] derive(char[] password, byte[] salt, int memoryKib,
            int iterations, int parallelism) {
        byte[] parameterSalt = salt.clone();
        Argon2Parameters.Builder builder = new Argon2Parameters.Builder(Argon2Parameters.ARGON2_id)
                .withVersion(ARGON2_VERSION)
                .withMemoryAsKB(memoryKib)
                .withIterations(iterations)
                .withParallelism(parallelism)
                .withSalt(parameterSalt);
        Argon2Parameters parameters = null;
        byte[] output = new byte[HASH_LENGTH];
        try {
            parameters = builder.build();
            Argon2BytesGenerator generator = new Argon2BytesGenerator();
            generator.init(parameters);
            generator.generateBytes(password, output);
            return output;
        } catch (RuntimeException exception) {
            Arrays.fill(output, (byte) 0);
            throw exception;
        } finally {
            if (parameters != null) {
                parameters.clear();
            }
            builder.clear();
            Arrays.fill(parameterSalt, (byte) 0);
        }
    }

    private static void validatePassword(char[] password) {
        if (password == null || password.length < MIN_PASSWORD_LENGTH
                || password.length > MAX_PASSWORD_LENGTH) {
            throw new IllegalArgumentException("Password length is outside the allowed range.");
        }
    }

    private record HashParameters(int memoryKib, int iterations, int parallelism) { }
}
