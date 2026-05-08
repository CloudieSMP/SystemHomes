# Seb's SystemHomes Plugin
![GitHub Actions Workflow Status](https://img.shields.io/github/actions/workflow/status/CloudieSMP/SystemHomes/Validate-Build.yml)

A customizable plugin for Minecraft Paper servers. This plugin allows players to sethomes, warps, playerwarps and tpa.

## Features

- Manage homes with `/sethome`, `/home`, and `/homes`.
- Teleport to other players with `/tpa`, `/tpaccept`, and `/tpdeny`.
- Manage warps with `/setwarp`, `/warp`, `/delwarp`, and `/warps`.
- Manage player warps with `/setpwarp`, `/pwarp`, `/delpwarp`, and `/pwarps`.
- Do `/spawn` to `/warp spawn`

## Commands

### TPA Commands
| Command             | Description                                     |
|---------------------|-------------------------------------------------|
| `/tpa <player>`     | Request to teleport to another player.          |
| `/tpahere <player>` | Request to teleport another player to yourself. |
| `/tpaccept`         | Accept a teleport request.                      |
| `/tpdeny`           | Deny a teleport request.                        |

### Home Commands
| Command           | Description                         |
|-------------------|-------------------------------------|
| `/sethome <name>` | Set a home with a specific name.    |
| `/delhome <name>` | Delete a home with a specific name. |
| `/home <name>`    | Teleport to a specific home.        |
| `/homes`          | List all your homes.                |

### Warp Commands
| Command           | Description                             |
|-------------------|-----------------------------------------|
| `/setwarp <name>` | Set a global warp with a specific name. |
| `/delwarp <name>` | Delete a specific warp.                 |
| `/warp <name>`    | Teleport to a specific warp.            |
| `/warps`          | List all available warps.               |
| `/spawn`          | Teleports to the spawn warp.            |

### Player Warp Commands
| Command            | Description                             |
|--------------------|-----------------------------------------|
| `/setpwarp <name>` | Set a player warp with a specific name. |
| `/delpwarp <name>` | Delete a player warp.                   |
| `/pwarp <name>`    | Teleport to a player warp.              |
| `/pwarps`          | List all available player warps.        |

## Configuration

The plugin's settings can be customized in the `config.yml` file. Below is an example configuration:

```yaml
home:
  teleport_delay: 2
warp:
  teleport_delay: 2
pwarp:
  teleport_delay: 2
  max_warps: 3
tpa:
  teleport_delay: 2
  request_timeout: 30
```

## Permissions
Do these even work atm?

| Admin Permission           | Description                                         |
|----------------------------|-----------------------------------------------------|
| `systemhomes.admin.*`      | Grants access to all admin perms.                   |
| `systemhomes.admin.reload` | Grants access to reload.                            |
| `systemhomes.admin.warp`   | Grants access to set and delete warp.               |
| `systemhomes.admin.pwarp`  | Grants access to delete and modify all PlayerWarps. |

| Player Permissions (default) | Description                                           |
|------------------------------|-------------------------------------------------------|
| `systemhomes.player.*`       | Grants access to all player perms.                    |
| `systemhomes.player.tpa`     | Grants access to use tpa and tpahere.                 |
| `systemhomes.player.home`    | Grants access to use and set homes.                   |
| `systemhomes.player.warp`    | Grants access to use warps.                           |
| `systemhomes.player.pwarp`   | Grants access to use, set and delete own playerwarps. |

## Installation

1. Download the plugin's `.jar` file.
2. Place the `.jar` file in your server's `plugins` folder.
3. Restart or reload your server.
4. Edit the `config.yml` file in the `plugins/SystemHomes` folder to customize settings.
5. Use the commands in-game to enjoy the plugin!

## Contributing

Contributions are welcome! Please fork the repository and submit a pull request with your changes.

## License
[![GNU GPLv3 License](https://img.shields.io/badge/License-GPLv3-green.svg)](https://choosealicense.com/licenses/gpl-3.0/)

This project is licensed under the GNU GPLv3 License. See the `LICENSE` file for details.
