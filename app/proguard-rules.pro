# Proguard rules for Untrusted VM
-keep class untrusted.vm.core.VmConfig { *; }
-keep class untrusted.vm.IVmService { *; }
-keep class untrusted.vm.IVmCallback { *; }
-keepclasseswithmembernames class * {
    native <methods>;
}
