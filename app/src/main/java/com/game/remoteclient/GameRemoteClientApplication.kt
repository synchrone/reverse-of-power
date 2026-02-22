package com.game.remoteclient

import android.app.Application
import com.game.remoteclient.network.NetworkManager

class GameRemoteClientApplication : Application() {

    val networkManager: NetworkManager by lazy { NetworkManager.getInstance() }

    override fun onTerminate() {
        networkManager.disconnect()
        super.onTerminate()
    }

    companion object {
        private lateinit var instance: GameRemoteClientApplication

        fun getInstance(): GameRemoteClientApplication = instance
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        networkManager.init(this)
    }
}
