package untrusted.vm;

import untrusted.vm.core.SuPolicyManager;

public class SuPolicyManagerTest {

    public static void testPoliciesAndLogging() {
        SuPolicyManager manager = new SuPolicyManager(1);

        assert manager.getDefaultPolicy() == SuPolicyManager.Policy.PROMPT : "Default should be PROMPT";

        manager.setPolicy("com.termux", SuPolicyManager.Policy.ALLOW);
        assert manager.getPolicy("com.termux") == SuPolicyManager.Policy.ALLOW : "Policy should be ALLOW";

        manager.setPolicy("com.unknown.bad", SuPolicyManager.Policy.DENY);
        assert manager.getPolicy("com.unknown.bad") == SuPolicyManager.Policy.DENY : "Policy should be DENY";

        // Root disabled blocks even ALLOWed apps
        boolean grantedWhenRootOff = manager.checkAndLogRequest("com.termux", "id", false);
        assert !grantedWhenRootOff : "Request must be denied when root is disabled";
        assert manager.getLogs().size() == 1 : "Should have 1 log entry";
        assert !manager.getLogs().get(0).isGranted() : "Log entry should record denied";

        // Root enabled respects policies
        boolean termuxGranted = manager.checkAndLogRequest("com.termux", "id", true);
        assert termuxGranted : "Termux request should be granted";

        boolean badAppDenied = manager.checkAndLogRequest("com.unknown.bad", "su", true);
        assert !badAppDenied : "Bad app request should be denied";

        // Interactive prompt callback
        manager.setPolicy("com.prompt.app", SuPolicyManager.Policy.PROMPT);
        manager.setPromptCallback(new SuPolicyManager.SuPromptCallback() {
            @Override
            public boolean onRequestPrompt(String pkg, String cmd) {
                return pkg.equals("com.prompt.app") && cmd.equals("id");
            }
        });

        boolean promptApproved = manager.checkAndLogRequest("com.prompt.app", "id", true);
        assert promptApproved : "Prompt should be approved for id";

        boolean promptRejected = manager.checkAndLogRequest("com.prompt.app", "rm -rf /", true);
        assert !promptRejected : "Prompt should be rejected for rm -rf";

        assert manager.getLogs().size() == 5 : "Should have 5 log entries";

        manager.clearLogs();
        assert manager.getLogs().isEmpty() : "Logs should be cleared";
    }
}
