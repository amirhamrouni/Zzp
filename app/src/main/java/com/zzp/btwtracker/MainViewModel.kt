package com.zzp.btwtracker

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.zzp.btwtracker.data.TransactionEntity
import com.zzp.btwtracker.data.ZzpDatabase
import com.zzp.btwtracker.tax.BelastingdienstAggregator
import com.zzp.btwtracker.tax.BelastingdienstReport
import com.zzp.btwtracker.tax.DutchVatEngine
import com.zzp.btwtracker.tax.Quarter
import com.zzp.btwtracker.tax.TransactionDraft
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val dao = ZzpDatabase.get(application).transactionDao()

    val transactions: StateFlow<List<TransactionEntity>> = dao.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _quarter = MutableStateFlow(Quarter.from(LocalDate.now()))
    val quarter: StateFlow<Quarter> = _quarter

    private val _report = MutableStateFlow(BelastingdienstAggregator.aggregate(_quarter.value, emptyList()))
    val report: StateFlow<BelastingdienstReport> = _report

    init { refreshReport() }

    fun save(draft: TransactionDraft, onDone: (Result<Long>) -> Unit = {}) {
        viewModelScope.launch {
            val result = runCatching { dao.insert(DutchVatEngine.toEntity(draft)) }
            result.onSuccess { refreshReport() }
            onDone(result)
        }
    }

    fun delete(id: Long) {
        viewModelScope.launch {
            dao.deleteById(id)
            refreshReport()
        }
    }

    fun moveQuarter(delta: Int) {
        val current = _quarter.value
        val absolute = current.year * 4 + (current.number - 1) + delta
        val year = Math.floorDiv(absolute, 4)
        val number = Math.floorMod(absolute, 4) + 1
        _quarter.value = Quarter(year, number)
        refreshReport()
    }

    fun refreshReport() {
        viewModelScope.launch {
            val q = _quarter.value
            val tx = dao.forPeriod(q.start.toEpochDay(), q.end.toEpochDay())
            _report.value = BelastingdienstAggregator.aggregate(q, tx)
        }
    }
}
