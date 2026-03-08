package com.ari.platform

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.modulith.Modulithic
import org.springframework.scheduling.annotation.EnableScheduling

@Modulithic(
    systemName = "ARI Platform",
    sharedModules = ["shared"]
)
@SpringBootApplication
@EnableScheduling
class AriPlatformApplication

fun main(args: Array<String>) {
    runApplication<AriPlatformApplication>(*args)
}
