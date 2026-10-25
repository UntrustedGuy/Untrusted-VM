package untrusted.vm;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

public class StandaloneTestRunner {

    public static void main(String[] args) {
        Class<?>[] testClasses = new Class<?>[] {
            VmConfigTest.class,
            RootControllerTest.class,
            SuPolicyManagerTest.class,
            VmFilesystemTest.class,
            VmEngineTest.class
        };

        int totalRan = 0;
        int passed = 0;
        int failed = 0;

        System.out.println("=================================================");
        System.out.println("   Untrusted VM - Deep Unit Test Verification");
        System.out.println("=================================================");

        for (Class<?> clazz : testClasses) {
            System.out.println("\nRunning test suite: " + clazz.getSimpleName());
            for (Method method : clazz.getDeclaredMethods()) {
                if (Modifier.isStatic(method.getModifiers()) &&
                    Modifier.isPublic(method.getModifiers()) &&
                    method.getName().startsWith("test")) {

                    totalRan++;
                    try {
                        method.invoke(null);
                        System.out.println("  [PASS] " + method.getName());
                        passed++;
                    } catch (Throwable t) {
                        Throwable cause = t.getCause() != null ? t.getCause() : t;
                        System.err.println("  [FAIL] " + method.getName() + ": " + cause.getMessage());
                        cause.printStackTrace();
                        failed++;
                    }
                }
            }
        }

        System.out.println("\n=================================================");
        System.out.println(String.format("Summary: Total: %d, Passed: %d, Failed: %d", totalRan, passed, failed));
        System.out.println("=================================================");

        if (failed > 0) {
            System.exit(1);
        }
    }
}
