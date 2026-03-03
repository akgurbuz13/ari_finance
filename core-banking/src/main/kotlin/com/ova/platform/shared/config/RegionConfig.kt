package com.ova.platform.shared.config

import com.ova.platform.shared.model.Region
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Configuration

@Configuration
class RegionConfig(
    @Value("\${ari.region}") private val regionCode: String
) {
    val region: Region = Region.fromCode(regionCode)

    fun isTurkey(): Boolean = region == Region.TR
    fun isEU(): Boolean = region == Region.EU
}
