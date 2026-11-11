package tech.annexflow.lvm.storage

import kotlinx.serialization.Serializable

@Serializable
data class Settings(val name: String = "", val volume: Int = 5)

@Serializable
data class Profile(val name: String, val age: Int = 0)

@Serializable
data class Shelf(val counts: Map<Profile, Int>)
