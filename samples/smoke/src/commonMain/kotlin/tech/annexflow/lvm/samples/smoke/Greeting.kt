package tech.annexflow.lvm.samples.smoke

expect fun platformName(): String

fun greeting(): String = "Hello from ${platformName()}"
