package untrusted.vm;

import untrusted.vm.IVmCallback;

interface IVmService {
    int getVmId();
    int getVmState();
    boolean startVm(String configJson);
    boolean stopVm();
    boolean isRootEnabled();
    boolean setRootEnabled(boolean enabled);
    String executeShellCommand(String command, boolean runAsRoot);
    void registerCallback(IVmCallback callback);
    void unregisterCallback(IVmCallback callback);
}
