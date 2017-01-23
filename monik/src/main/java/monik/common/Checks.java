package monik.common;

public final class Checks {
    private Checks() {
    }

    public static <T> T checkArgNotNull(T arg, String argName) {
        if (arg == null) {
            throw new IllegalArgumentException("'" + argName + "' is null.");
        }
        return arg;
    }
}
