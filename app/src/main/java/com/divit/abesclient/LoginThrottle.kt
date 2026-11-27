package com.divit.abesclient

/**
 * Caps automatic portal logins so a flapping connection can never hammer the campus portal (which bans
 * users that send too many requests): at least [minGapMs] between attempts, at most [maxPerWindow] per [windowMs].
 */
class LoginThrottle(
    private val minGapMs: Long = 30_000, private val maxPerWindow: Int = 3, private val windowMs: Long = 600_000,
) {
    private val attempts = ArrayDeque<Long>()

    /** True (and counts the attempt) if a login may be sent at [now]. */
    @Synchronized fun allow(now: Long = System.currentTimeMillis()): Boolean {
        while (attempts.isNotEmpty() && now - attempts.first() >= windowMs) attempts.removeFirst()
        if (attempts.size >= maxPerWindow || (attempts.isNotEmpty() && now - attempts.last() < minGapMs)) return false
        attempts.addLast(now)
        return true
    }
}
