package com.lipabill.app.viewmodel

import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel

/**
 * One [EngagementViewModel] per activity so Home, Habits, and Insights share
 * challenge Done ack, check-in, and streak state.
 */
@Composable
fun activityEngagementViewModel(): EngagementViewModel {
    val activity = checkNotNull(LocalActivity.current as? ComponentActivity) {
        "activityEngagementViewModel requires a ComponentActivity"
    }
    return viewModel(viewModelStoreOwner = activity)
}
