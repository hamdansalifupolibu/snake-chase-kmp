package com.snakechase.app.online

/**
 * Decides an online ghost duel. Pure function: both players' apps (and the inbox) apply the
 * same rule to the same two runs, so everyone sees the same verdict.
 *
 * Round 1 FLEE: longer survival is better (fleeMs, max 45 000 = survived the whole round).
 * Round 2 HUNT: faster catch is better (huntMs, 45 000 = never caught the prey).
 */
object OnlineDuelRules {

    fun decide(mine: OnlineRun, theirs: OnlineRun): OnlineVerdict {
        val wonFlee = mine.fleeMs > theirs.fleeMs
        val wonHunt = mine.huntMs < theirs.huntMs
        // Positive = in my favour: extra seconds survived plus seconds saved hunting.
        val marginMs = (mine.fleeMs - theirs.fleeMs) + (theirs.huntMs - mine.huntMs)

        val iWon = when {
            wonFlee && wonHunt -> true
            !wonFlee && !wonHunt -> false
            // 1–1 split: the bigger combined time advantage takes it. An exact tie goes to the
            // posted run — the challenger has to *beat* the ghost, matching it isn't enough —
            // so both sides always agree on one real winner (the security rules require one).
            else -> marginMs > 0
        }
        return OnlineVerdict(iWon = iWon, wonFlee = wonFlee, wonHunt = wonHunt, marginMs = marginMs)
    }
}
