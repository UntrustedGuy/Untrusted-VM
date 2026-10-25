package untrusted.vm.service

/**
 * Maps VM instances to isolated Android service processes (:vms01 - :vms04),
 * mirroring VMOS Pro's multi-process container isolation.
 */
enum class VmProcessSlot(val processName: String, val serviceClass: Class<*>) {
    SLOT_1(":vms01", VmService01::class.java),
    SLOT_2(":vms02", VmService02::class.java),
    SLOT_3(":vms03", VmService03::class.java),
    SLOT_4(":vms04", VmService04::class.java);

    companion object {
        fun forInstanceId(instanceId: Int): VmProcessSlot {
            val idx = (instanceId - 1) % entries.size
            return entries[if (idx < 0) 0 else idx]
        }
    }
}
