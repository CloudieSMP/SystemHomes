import org.spongepowered.configurate.objectmapping.ConfigSerializable

@ConfigSerializable
data class Config(
    val language: String = "en",
    val home: HomeConfig = HomeConfig(),
    val tpa: TpaConfig = TpaConfig(),
    val warp: WarpConfig = WarpConfig(),
    val pwarp: PWarpConfig = PWarpConfig()
)

@ConfigSerializable
data class HomeConfig(
    val enable: Boolean = true,
    val teleportDelay: Int = 2,
    val maxHomes: Int = 3,
)

@ConfigSerializable
data class TpaConfig(
    val enable: Boolean = true,
    val requestExpireTime: Int = 30,
    val teleportDelay: Int = 2,
)

@ConfigSerializable
data class WarpConfig(
    val enable: Boolean = true,
    val teleportDelay: Int = 2,
)

@ConfigSerializable
data class PWarpConfig(
    val enable: Boolean = true,
    val teleportDelay: Int = 2,
    val maxWarps: Int = 3,
)