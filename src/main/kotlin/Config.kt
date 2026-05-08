import org.spongepowered.configurate.objectmapping.ConfigSerializable

@ConfigSerializable
data class Config(
    val tpa: TpaConfig = TpaConfig(),
    val home: HomeConfig = HomeConfig(),
)

@ConfigSerializable
data class TpaConfig(
    val requestExpireTime: Int = 30,
    val tpaDelay: Int = 2
)

@ConfigSerializable
data class HomeConfig(
    val maxHomes: Int = 3
)