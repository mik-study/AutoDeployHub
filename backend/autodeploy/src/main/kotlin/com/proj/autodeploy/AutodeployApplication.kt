package com.proj.autodeploy

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class AutodeployApplication

fun main(args: Array<String>) {
	runApplication<AutodeployApplication>(*args)
}
