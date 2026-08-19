package com.conice.morss.domain.model.account

sealed class SyncOnStartPreference(
    val value: Boolean,
) {

    object On : SyncOnStartPreference(true)
    object Off : SyncOnStartPreference(false)

    companion object {

        val default = Off
        val values = listOf(On, Off)
    }
}

operator fun SyncOnStartPreference.not(): SyncOnStartPreference =
    when (value) {
        true -> SyncOnStartPreference.Off
        false -> SyncOnStartPreference.On
    }
