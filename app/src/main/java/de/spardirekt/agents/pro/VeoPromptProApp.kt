package de.spardirekt.agents.pro

import android.app.Application
import de.spardirekt.agents.pro.data.db.AppDatabase
import de.spardirekt.agents.pro.data.repository.ProjectRepository
import de.spardirekt.agents.pro.generation.GenerationManager
import de.spardirekt.agents.pro.network.OpenAiClient
import de.spardirekt.agents.pro.storage.SecureApiKeyStore
import de.spardirekt.agents.pro.storage.SettingsStore

class VeoPromptProApp : Application() {

    lateinit var apiKeyStore: SecureApiKeyStore
        private set
    lateinit var settingsStore: SettingsStore
        private set
    lateinit var projectRepository: ProjectRepository
        private set
    lateinit var generationManager: GenerationManager
        private set
    lateinit var openAiClient: OpenAiClient
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        apiKeyStore = SecureApiKeyStore(this)
        settingsStore = SettingsStore(this)
        openAiClient = OpenAiClient()
        val db = AppDatabase.get(this)
        projectRepository = ProjectRepository(db.projectDao())
        generationManager = GenerationManager(
            appContext = this,
            repository = projectRepository,
            apiKeyStore = apiKeyStore,
            settingsStore = settingsStore,
            openAi = openAiClient
        )
    }

    companion object {
        lateinit var instance: VeoPromptProApp
            private set
    }
}
