package app.vitune.android.preferences

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import app.vitune.android.GlobalPreferencesHolder
import app.vitune.android.R
import app.vitune.core.data.enums.CoilDiskCacheSize
import app.vitune.core.data.enums.ExoPlayerDiskCacheSize
import app.vitune.providers.innertube.Innertube
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours

object DataPreferences : GlobalPreferencesHolder() {
    var coilDiskCacheMaxSize by enum(CoilDiskCacheSize.`128MB`)
    var exoPlayerDiskCacheMaxSize by enum(ExoPlayerDiskCacheSize.`2GB`)
    var pauseHistory by boolean(false)
    var pausePlaytime by boolean(false)
    var pauseSearchHistory by boolean(false)
    val topListLengthProperty = int(50)
    var topListLength by topListLengthProperty
    val topListPeriodProperty = enum(TopListPeriod.AllTime)
    var topListPeriod by topListPeriodProperty
    var quickPicksSource by enum(QuickPicksSource.Trending)
    var versionCheckPeriod by enum(VersionCheckPeriod.Off)
    var shouldCacheQuickPicks by boolean(true)
    var cachedQuickPicks by json(Innertube.RelatedPage())
    var autoSyncPlaylists by boolean(true)
    var showRetrospectiveAllYear by boolean(false)

    enum class TopListPeriod(
        val displayName: @Composable () -> String,
        val duration: Duration? = null
    ) {
        PastDay(displayName = { stringResource(R.string.past_24_hours) }, duration = 1.days),
        PastWeek(displayName = { stringResource(R.string.past_week) }, duration = 7.days),
        PastMonth(displayName = { stringResource(R.string.past_month) }, duration = 30.days),
        PastYear(displayName = { stringResource(R.string.past_year) }, 365.days),
        AllTime(displayName = { stringResource(R.string.all_time) })
    }

    enum class QuickPicksSource(val displayName: @Composable () -> String) {
        Trending(displayName = { stringResource(R.string.trending) }),
        LastInteraction(displayName = { stringResource(R.string.last_interaction) })
    }

    enum class VersionCheckPeriod(
        val displayName: @Composable () -> String,
        val period: Duration?
    ) {
        Off(displayName = { stringResource(R.string.off_text) }, period = null),
        Hourly(displayName = { stringResource(R.string.hourly) }, period = 1.hours),
        Daily(displayName = { stringResource(R.string.daily) }, period = 1.days),
        Weekly(displayName = { stringResource(R.string.weekly) }, period = 7.days)
    }
}
