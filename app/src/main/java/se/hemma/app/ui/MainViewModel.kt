package se.hemma.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import se.hemma.app.data.Machine
import se.hemma.app.data.PriceData
import se.hemma.app.data.PriceRepository
import se.hemma.app.data.PriceSettings
import se.hemma.app.data.STOCKHOLM
import se.hemma.app.data.SettingsStore
import se.hemma.app.domain.Dashboard
import se.hemma.app.widget.WidgetUpdater
import java.time.LocalDate
import java.time.ZonedDateTime

data class UiState(
    val settings: PriceSettings,
    val machines: List<Machine>,
    val prices: PriceData = PriceData.EMPTY,
    val dashboard: Dashboard? = null,
    val loading: Boolean = false,
    val error: String? = null,
)

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = PriceRepository(app)
    private val store = SettingsStore(app)

    private val _state = MutableStateFlow(
        UiState(settings = store.loadSettings(), machines = store.loadMachines()),
    )
    val state: StateFlow<UiState> = _state.asStateFlow()

    private var refreshJob: Job? = null
    private var widgetJob: Job? = null
    private var loadedDate: LocalDate? = null
    private var lastNetworkAttempt = 0L

    init {
        refresh()
        // Håll "just nu" aktuellt och hämta morgondagens priser när de släpps.
        viewModelScope.launch {
            while (isActive) {
                delay(30_000)
                val now = ZonedDateTime.now(STOCKHOLM)
                val waitingForTomorrow = now.hour >= 13 && _state.value.prices.tomorrow.isEmpty() &&
                    System.currentTimeMillis() - lastNetworkAttempt > 10 * 60_000
                if (loadedDate != now.toLocalDate() || waitingForTomorrow) refresh() else rebuild()
            }
        }
    }

    fun onResume() {
        refresh()
    }

    /** Läser cache direkt och hämtar sedan det som saknas från nätet. */
    fun refresh(force: Boolean = false) {
        if (refreshJob?.isActive == true) {
            if (!force) return
            refreshJob?.cancel()
        }
        refreshJob = viewModelScope.launch {
            val area = _state.value.settings.area
            _state.update { it.copy(loading = true) }

            val cached = repo.load(area, allowNetwork = false)
            if (cached.data.today.isNotEmpty()) setPrices(cached.data, _state.value.error)

            lastNetworkAttempt = System.currentTimeMillis()
            val res = repo.load(area, allowNetwork = true)
            setPrices(res.data, res.error)
            _state.update { it.copy(loading = false) }
            WidgetUpdater.renderAll(getApplication<Application>(), res.data)
        }
    }

    fun updateSettings(new: PriceSettings) {
        val old = _state.value.settings
        if (new == old) return
        store.saveSettings(new)
        if (new.area != old.area) {
            _state.update { it.copy(settings = new, prices = PriceData.EMPTY, dashboard = null, error = null) }
            refresh(force = true)
        } else {
            _state.update { it.copy(settings = new) }
            rebuild()
            scheduleWidgetUpdate()
        }
    }

    fun saveMachine(machine: Machine) {
        val list = _state.value.machines.toMutableList()
        val i = list.indexOfFirst { it.id == machine.id }
        if (i >= 0) list[i] = machine else list.add(machine)
        setMachines(list)
    }

    fun deleteMachine(id: String) {
        setMachines(_state.value.machines.filterNot { it.id == id })
    }

    fun moveMachine(id: String, delta: Int) {
        val list = _state.value.machines.toMutableList()
        val i = list.indexOfFirst { it.id == id }
        val j = i + delta
        if (i < 0 || j !in list.indices) return
        val m = list.removeAt(i)
        list.add(j, m)
        setMachines(list)
    }

    private fun setMachines(list: List<Machine>) {
        store.saveMachines(list)
        _state.update { it.copy(machines = list) }
        rebuild()
        scheduleWidgetUpdate()
    }

    private fun setPrices(data: PriceData, error: String?) {
        loadedDate = ZonedDateTime.now(STOCKHOLM).toLocalDate()
        _state.update { it.copy(prices = data, error = error) }
        rebuild()
    }

    private fun rebuild() {
        _state.update {
            it.copy(dashboard = Dashboard.build(it.prices, it.settings, it.machines, ZonedDateTime.now(STOCKHOLM)))
        }
    }

    /** Uppdaterar widgeten en kort stund efter senaste ändringen (inte för varje tangenttryck). */
    private fun scheduleWidgetUpdate() {
        widgetJob?.cancel()
        widgetJob = viewModelScope.launch {
            delay(800)
            WidgetUpdater.renderAll(getApplication<Application>(), _state.value.prices)
        }
    }
}
