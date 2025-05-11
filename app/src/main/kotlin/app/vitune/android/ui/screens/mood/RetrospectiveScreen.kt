package app.vitune.android.ui.screens.mood

import androidx.compose.runtime.Composable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import app.vitune.android.R
import app.vitune.android.ui.components.themed.Scaffold
import app.vitune.android.ui.screens.GlobalRoutes
import app.vitune.android.ui.screens.Route
import app.vitune.compose.persist.PersistMapCleanup
import app.vitune.compose.routing.RouteHandler

@Route
@Composable
fun RetrospectiveScreen() {
    val saveableStateHolder = rememberSaveableStateHolder()

    PersistMapCleanup(prefix = "retrospective")

    RouteHandler {
        GlobalRoutes()

        Content {
            Scaffold(
                key = "retrospective",
                topIconButtonId = R.drawable.chevron_back,
                onTopIconButtonClick = pop,
                tabIndex = 0,
                onTabChange = { },
                tabColumnContent = {
                    tab(0, R.string.retrospective, R.drawable.disc)
                    /*
                        - Top listened artists
                        - Top listened songs
                        - Top genders
                        - Total minutes listened
                     */
                }
            ) { currentTabIndex ->
                saveableStateHolder.SaveableStateProvider(key = currentTabIndex) {
                    when (currentTabIndex) {
                        0 -> null
                    }
                }
            }
        }
    }
}