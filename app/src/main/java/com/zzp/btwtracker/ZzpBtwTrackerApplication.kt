package com.zzp.btwtracker

import android.app.Application
import com.zzp.btwtracker.data.ZzpDatabase

class ZzpBtwTrackerApplication : Application() {
    val database: ZzpDatabase by lazy { ZzpDatabase.get(this) }
}
