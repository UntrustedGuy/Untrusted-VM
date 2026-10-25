package untrusted.vm.core;

/**
 * Lifecycle states of an Untrusted VM instance.
 */
public enum VmState {
    STOPPED(0, "Stopped"),
    STARTING(1, "Starting..."),
    RUNNING(2, "Running"),
    PAUSED(3, "Paused"),
    STOPPING(4, "Stopping..."),
    ERROR(5, "Error");

    private final int code;
    private final String label;

    VmState(int code, String label) {
        this.code = code;
        this.label = label;
    }

    public int getCode() {
        return code;
    }

    public String getLabel() {
        return label;
    }

    public static VmState fromCode(int code) {
        for (VmState s : values()) {
            if (s.code == code) {
                return s;
            }
        }
        return STOPPED;
    }
}
