import event.PlayerJoin
import io.papermc.paper.command.brigadier.CommandSourceStack
import org.bukkit.plugin.java.JavaPlugin
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
        registerCommands()
        server.pluginManager.registerEvents(PlayerJoin(), this)
    }

    override fun onDisable() {
        this.logger.info("Stopping SystemHomes!")
    }

    private fun registerCommands() {
        commandManager = PaperCommandManager.builder()
            .executionCoordinator(ExecutionCoordinator.simpleCoordinator())
            .buildOnEnable(this)

        annotationParser = AnnotationParser(commandManager, CommandSourceStack::class.java)
        annotationParser.parseContainers()
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
        logger.info("Loaded configuration.")
    }
}

val plugin: SystemHomes get() = JavaPlugin.getPlugin(SystemHomes::class.java)
val logger get() = plugin.logger