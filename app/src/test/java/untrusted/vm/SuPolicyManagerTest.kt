package untrusted.vm

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import untrusted.vm.core.SuPolicyManager

class SuPolicyManagerTest {

    private lateinit var policyManager: SuPolicyManager

    @Before
    fun setUp() {
        policyManager = SuPolicyManager(vmId = 1)
    }

    @Test
    fun testDefaultPolicyAndOverride() {
        assertEquals(SuPolicyManager.Policy.PROMPT, policyManager.getPolicy("com.example.app"))

        policyManager.setPolicy("com.example.app", SuPolicyManager.Policy.ALLOW)
        assertEquals(SuPolicyManager.Policy.ALLOW, policyManager.getPolicy("com.example.app"))

        policyManager.setPolicy("com.malicious.tool", SuPolicyManager.Policy.DENY)
        assertEquals(SuPolicyManager.Policy.DENY, policyManager.getPolicy("com.malicious.tool"))
    }

    @Test
    fun testRootDisabledBlocksAllRequests() {
        policyManager.setPolicy("com.example.app", SuPolicyManager.Policy.ALLOW)
        val granted = policyManager.checkAndLogRequest("com.example.app", "id", isRootEnabled = false)
        assertFalse(granted)

        val logs = policyManager.getLogs()
        assertEquals(1, logs.size)
        assertEquals("com.example.app", logs[0].packageName)
        assertFalse(logs[0].granted)
    }

    @Test
    fun testRootEnabledRespectsPolicies() {
        policyManager.setPolicy("com.allowed.app", SuPolicyManager.Policy.ALLOW)
        policyManager.setPolicy("com.denied.app", SuPolicyManager.Policy.DENY)

        val allowed = policyManager.checkAndLogRequest("com.allowed.app", "whoami", isRootEnabled = true)
        val denied = policyManager.checkAndLogRequest("com.denied.app", "reboot", isRootEnabled = true)

        assertTrue(allowed)
        assertFalse(denied)

        val logs = policyManager.getLogs()
        assertEquals(2, logs.size)
        assertTrue(logs[0].granted)
        assertFalse(logs[1].granted)
    }
}
