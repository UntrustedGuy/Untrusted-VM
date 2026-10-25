package untrusted.vm;

interface IVmCallback {
    void onStateChanged(int state, String message);
    void onLogOutput(String line);
    void onRootStatusChanged(boolean isRootEnabled);
}
