package untrusted.vm.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages root authorization policies per guest package within a VM instance.
 * Analogous to VMOS Pro's Superuser Manager and Magisk policy manager.
 */
public class SuPolicyManager {

    public enum Policy {
        ALLOW,
        DENY,
        PROMPT
    }

    public static class SuRequestLog {
        private final String packageName;
        private final String command;
        private final long timestamp;
        private final boolean granted;

        public SuRequestLog(String packageName, String command, long timestamp, boolean granted) {
            this.packageName = packageName;
            this.command = command;
            this.timestamp = timestamp;
            this.granted = granted;
        }

        public String getPackageName() { return packageName; }
        public String getCommand() { return command; }
        public long getTimestamp() { return timestamp; }
        public boolean isGranted() { return granted; }
    }

    private final int vmId;
    private final ConcurrentHashMap<String, Policy> packagePolicies = new ConcurrentHashMap<>();
    private final List<SuRequestLog> requestLogs = new ArrayList<>();
    private Policy defaultPolicy = Policy.PROMPT;

    public SuPolicyManager(int vmId) {
        this.vmId = vmId;
    }

    public void setPolicy(String packageName, Policy policy) {
        packagePolicies.put(packageName, policy);
    }

    public Policy getPolicy(String packageName) {
        Policy p = packagePolicies.get(packageName);
        return p != null ? p : defaultPolicy;
    }

    public void setDefaultPolicy(Policy policy) {
        this.defaultPolicy = policy;
    }

    public Policy getDefaultPolicy() {
        return defaultPolicy;
    }

    public interface SuPromptCallback {
        boolean onRequestPrompt(String packageName, String command);
    }

    private volatile SuPromptCallback promptCallback;

    public void setPromptCallback(SuPromptCallback callback) {
        this.promptCallback = callback;
    }

    public SuPromptCallback getPromptCallback() {
        return promptCallback;
    }

    public boolean checkAndLogRequest(String packageName, String command, boolean isRootEnabled) {
        if (!isRootEnabled) {
            log(packageName, command, false);
            return false;
        }
        Policy policy = getPolicy(packageName);
        boolean granted = false;
        if (policy == Policy.ALLOW) {
            granted = true;
        } else if (policy == Policy.DENY) {
            granted = false;
        } else if (policy == Policy.PROMPT) {
            if (promptCallback != null) {
                granted = promptCallback.onRequestPrompt(packageName, command);
            } else {
                granted = true; // Permissive fallback when interactive UI prompt handler is unset
            }
        }
        log(packageName, command, granted);
        return granted;
    }

    private synchronized void log(String packageName, String command, boolean granted) {
        requestLogs.add(new SuRequestLog(packageName, command, System.currentTimeMillis(), granted));
        if (requestLogs.size() > 200) {
            requestLogs.remove(0);
        }
    }

    public synchronized List<SuRequestLog> getLogs() {
        return Collections.unmodifiableList(new ArrayList<>(requestLogs));
    }

    public synchronized void clearLogs() {
        requestLogs.clear();
    }
}
