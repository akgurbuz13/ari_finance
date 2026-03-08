package com.ari.blockchain

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableScheduling

@SpringBootApplication
@EnableScheduling
class AriBlockchainApplication

fun main(args: Array<String>) {
    runApplication<AriBlockchainApplication>(*args)
}
