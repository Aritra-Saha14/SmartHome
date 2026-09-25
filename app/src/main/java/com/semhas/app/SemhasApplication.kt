package com.semhas.app

import android.app.Application
import com.semhas.app.data.mock.MockSemhasRepository
import com.semhas.app.data.repository.SemhasRepository
import com.semhas.app.data.repository.SupabaseSemhasRepository
import com.semhas.app.voice.VoiceAssistantManager

class SemhasApplication : Application() {

    companion object {
        lateinit var instance: SemhasApplication
            private set
    }

    val repository: SemhasRepository by lazy {
        SupabaseSemhasRepository(context = this)
    }

    val voiceAssistantManager: VoiceAssistantManager by lazy {
        VoiceAssistantManager(this, repository)
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
    }
}
