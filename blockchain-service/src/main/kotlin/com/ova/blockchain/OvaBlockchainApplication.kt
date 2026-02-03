package com.ova.blockchain

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableScheduling

@SpringBootApplication
@EnableScheduling
class OvaBlockchainApplication

fun main(args: Array<String>) {
    runApplication<OvaBlockchainApplication>(*args)
}
