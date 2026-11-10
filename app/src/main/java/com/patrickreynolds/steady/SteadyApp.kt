package com.patrickreynolds.steady

import android.app.Application
import com.patrickreynolds.steady.reminders.Reminders
import com.patrickreynolds.steady.steps.PhoneSteps

class SteadyApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Reminders.createChannel(this)
        Reminders.schedule(this)
        PhoneSteps.schedule(this)
    }
}
