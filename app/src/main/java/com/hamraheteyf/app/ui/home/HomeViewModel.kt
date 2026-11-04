package com.hamraheteyf.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hamraheteyf.app.community.CommunityClient
import com.hamraheteyf.app.community.HomeFeed
import com.hamraheteyf.app.net.API_UNAUTHORIZED
import com.hamraheteyf.app.net.ApiResult
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class HomeState(
    val feed: HomeFeed? = null,
    val loading: Boolean = false,
    val refreshing: Boolean = false,
    val error: String? = null,
)

/** Loads the home feed (forumHome) — hot topics, expert answers, learning items, fresh topics. */
class HomeViewModel(private val client: CommunityClient?) : ViewModel() {
    private val _state = MutableStateFlow(HomeState(loading = client != null))
    val state: StateFlow<HomeState> get() = _state
    private var job: Job? = null

    init { load(user = false) }

    /** [user] = pull-to-refresh (shows the spinner); otherwise a quiet reload that keeps current content on screen. */
    fun load(user: Boolean) {
        val c = client ?: run { _state.value = HomeState(feed = HomeFeed()); return }
        if (job?.isActive == true) return
        val s = _state.value
        _state.value = s.copy(loading = s.feed == null, refreshing = user, error = null)
        job = viewModelScope.launch {
            _state.value = when (val r = c.home()) {
                is ApiResult.Ok -> HomeState(feed = r.data)
                is ApiResult.Fail -> _state.value.copy(
                    loading = false, refreshing = false,
                    // keep showing the last good feed; only an empty screen turns into an error view
                    error = when {
                        r.message == API_UNAUTHORIZED -> "نشستِ شما منقضی شده است."
                        r.network -> "اتصال به سرور برقرار نشد. اینترنت را بررسی کنید."
                        else -> r.message
                    },
                )
            }
        }
    }
}
