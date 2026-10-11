package app.foco.blocker

import android.content.Context
import android.content.Intent
import android.util.Log
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** What an engine must do about the app in the foreground. */
sealed class Decision {
  abstract val block: AppBlock

  /** Hard block screen, no way through. */
  data class Block(override val block: AppBlock) : Decision()

  /** 10 s pause with "Volver" / "Abrir igualmente" (5 min pass). */
  data class Friction(override val block: AppBlock) : Decision()

  /** Only some surfaces are blocked and the accessibility tree can tell which one is on screen. */
  data class Surfaces(override val block: AppBlock) : Decision()
}

enum class Engine(val id: String) { ACCESSIBILITY("accessibility"), USAGE("usage") }

/**
 * Rules shared by both engines, so the accessibility service and the
 * usage-access fallback behave the same way and never disagree.
 */
object BlockEngine {
  private const val TAG = "FocoBlocker"
  private const val STATS_COOLDOWN_MS = 3_500L
  private const val LIMIT_CACHE_MS = 30_000L

  private val lastStatAt = HashMap<String, Long>()
  private val limitCache = HashMap<String, Triple<Long, Long, Double>>() // appId → (dayStart, computedAt, minutes)

  fun decide(context: Context, pkg: String, engine: Engine, now: Long = System.currentTimeMillis()): Decision? {
    if (pkg == context.packageName) return null
    val state = BlockState.load(context)
    val block = state.blockFor(pkg, now) ?: limitBlock(context, state, pkg, now) ?: return null
    return when {
      block.wholeApp && (block.strict || !state.wholeAppFriction) -> Decision.Block(block)
      block.wholeApp -> if (Allowances.isAllowed(context, block.appId, now)) null else Decision.Friction(block)
      engine == Engine.ACCESSIBILITY -> Decision.Surfaces(block)
      // Without accessibility the section on screen cannot be known: a strict granular block
      // becomes a whole-app block (no way through); a non-strict one gets the pause screen.
      block.strict -> Decision.Block(block)
      else -> if (Allowances.isAllowed(context, block.appId, now)) null else Decision.Friction(block)
    }
  }

  /** Daily limit reached since the last setState (JS only knows usage when it runs). */
  private fun limitBlock(context: Context, state: BlockState, pkg: String, now: Long): AppBlock? {
    val limit = state.limitFor(pkg) ?: return null
    // Usage since local midnight; the cache is keyed by day so it resets at midnight.
    val dayStart = UsageAccess.startOfToday()
    val minutes = synchronized(limitCache) {
      val cached = limitCache[limit.appId]
      if (cached != null && cached.first == dayStart && now >= cached.second && now - cached.second < LIMIT_CACHE_MS) cached.third
      else {
        val used = UsageAccess.minutesToday(context, limit.packages, now).values.sum()
        limitCache[limit.appId] = Triple(dayStart, now, used)
        used
      }
    }
    if (minutes < limit.minutesPerDay) return null
    return AppBlock(limit.appId, limit.packages, setOf(AppBlock.SURFACE_APP), limit.reason, strict = false)
  }

  fun recordAttempt(context: Context, appId: String, now: Long = System.currentTimeMillis()) {
    val count = synchronized(lastStatAt) {
      val last = lastStatAt[appId] ?: 0L
      lastStatAt[appId] = now
      now - last > STATS_COOLDOWN_MS
    }
    if (count) BlockStats.record(context, appId, today())
  }

  /** Shows BlockActivity for a Block or Friction decision. Returns false if Android refused the launch. */
  fun showScreen(context: Context, decision: Decision, pkg: String, engine: Engine): Boolean {
    val mode = when (decision) {
      is Decision.Block -> BlockActivity.MODE_BLOCK
      is Decision.Friction -> BlockActivity.MODE_FRICTION
      is Decision.Surfaces -> return false
    }
    recordAttempt(context, decision.block.appId)
    val intent = Intent(context, BlockActivity::class.java)
      .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)
      .putExtra(BlockActivity.EXTRA_MODE, mode)
      .putExtra(BlockActivity.EXTRA_REASON, decision.block.reason)
      .putExtra(BlockActivity.EXTRA_APP, decision.block.appId)
      .putExtra(BlockActivity.EXTRA_PACKAGE, pkg)
      .putExtra(BlockActivity.EXTRA_ENGINE, engine.id)
    return try {
      context.startActivity(intent)
      true
    } catch (e: RuntimeException) {
      // Background activity start refused (no "display over other apps" permission) or similar.
      Log.w(TAG, "Could not show the block screen", e)
      false
    }
  }

  fun goHome(context: Context) {
    runCatching {
      context.startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
  }

  fun today(): String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
}
