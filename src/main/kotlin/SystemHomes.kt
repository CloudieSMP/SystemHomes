import command.PlayerWarp
import command.Home
import command.Warp
import command.Tpa
import event.PlayerJoin
import io.papermc.paper.command.brigadier.CommandSourceStack
import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.plugin.java.JavaPlugin
import util.HomeStorage
import util.Lang
import util.LegacyMigration
import util.UpdateChecker
import org.incendo.cloud.annotations.AnnotationParser
import org.incendo.cloud.execution.ExecutionCoordinator
import org.incendo.cloud.paper.PaperCommandManager
import org.spongepowered.configurate.kotlin.extensions.get
import org.spongepowered.configurate.kotlin.objectMapperFactory
import org.spongepowered.configurate.yaml.YamlConfigurationLoader
import java.io.File

class SystemHomes : JavaPlugin() {
    private lateinit var commandManager: PaperCommandManager<CommandSourceStack>
    private lateinit var annotationParser: AnnotationParser<CommandSourceStack>
    lateinit var config: Config
        private set

    override fun onEnable() {
        this.logger.info("Starting SystemHomes!")
        reloadConfig()
        LegacyMigration.importLegacyData()
        registerCommands()
        server.pluginManager.registerEvents(PlayerJoin(), this)
        UpdateChecker(this).checkForUpdates()
    }

    override fun onDisable() {
        this.logger.info(Lang.raw("startup.stopping"))
        HomeStorage.flushAllSync()
    }

    private fun registerCommands() {
        commandManager = PaperCommandManager.builder()
            .executionCoordinator(ExecutionCoordinator.simpleCoordinator())
            .buildOnEnable(this)

        annotationParser = AnnotationParser(commandManager, CommandSourceStack::class.java)

        if (config.home.enable) {
            annotationParser.parse(Home())
        } else {
            logger.info(Lang.raw("startup.home-disabled"))
        }

        if (config.tpa.enable) {
            annotationParser.parse(Tpa())
        } else {
            logger.info(Lang.raw("startup.tpa-disabled"))
        }

        if (config.warp.enable) {
            annotationParser.parse(Warp())
        } else {
            logger.info(Lang.raw("startup.warp-disabled"))
        }

        if (config.pwarp.enable) {
            annotationParser.parse(PlayerWarp())
        } else {
            logger.info(Lang.raw("startup.pwarp-disabled"))
        }
    }

    override fun reloadConfig() {
        if (!dataFolder.exists()) dataFolder.mkdir()
        val configFile = File(dataFolder, "config.yml")

        if (!configFile.exists()) {
            getResource("config.yml").use { inputStream ->
                configFile.outputStream().use { outputStream ->
                    inputStream!!.copyTo(outputStream)
                }
            }
        }

        val loader = YamlConfigurationLoader.builder()
            .file(configFile)
            .defaultOptions { options ->
                options.serializers { builder ->
                    builder.registerAnnotatedObjects(objectMapperFactory())
                }
            }
            .build()

        val node = loader.load()
        config = node.get(Config::class)!!
        Lang.load(config.language)
        logger.info(Lang.raw("startup.loaded-config"))
    }
}

val plugin: SystemHomes get() = JavaPlugin.getPlugin(SystemHomes::class.java)
val logger get() = plugin.logger
val mm: MiniMessage get() = MiniMessage.miniMessage()