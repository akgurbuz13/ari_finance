package com.ova.platform

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.modulith.Modulithic
import org.springframework.scheduling.annotation.EnableScheduling

@Modulithic(
    systemName = "Ova Platform",
    sharedModules = ["shared"]
)
@SpringBootApplication
@EnableScheduling
class OvaPlatformApplication

fun main(args: Array<String>) {
    runApplication<OvaPlatformApplication>(*args)
}
