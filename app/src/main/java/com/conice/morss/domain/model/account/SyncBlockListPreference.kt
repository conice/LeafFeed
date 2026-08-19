package com.conice.morss.domain.model.account

typealias SyncBlockList = List<String>

object SyncBlockListPreference {

    val default: SyncBlockList = emptyList()

    fun of(syncBlockList: String): SyncBlockList {
        return syncBlockList.split("\n")
    }

    fun toString(syncBlockList: SyncBlockList): String = syncBlockList
        .filter { it.isNotBlank() }
        .map { it.trim() }
        .joinToString { "$it\n" }
}
