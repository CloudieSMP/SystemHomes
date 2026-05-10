# Seb's SystemHomes Plugin
![GitHub Actions Workflow Status](https://img.shields.io/github/actions/workflow/status/CloudieSMP/SystemHomes/build.yml)

A customizable plugin for Minecraft Paper servers. This plugin allows players to sethomes, warps, playerwarps and tpa.

## Features

- Manage homes with `/sethome`, `/home`, and `/homes`.
- Teleport to other players with `/tpa`, `/tpaccept`, and `/tpdeny`.
- Manage global warps with `/setwarp`, `/warp`, `/delwarp`, `/warps`, and `/spawn`.
- Manage player warps with `/setpwarp`, `/pwarp`, `/delpwarp`, and `/pwarps`.
- Do `/spawn` to `/warp spawn`
- Automatically import legacy data from the old `SystemHomes` on startup.
- Check GitHub releases for updates on startup.
- Store all global warps in one `warps.yml` file.
- Store player warps per player UUID in `playerwarps/<uuid>.yml`.

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
# Language file to use from the lang/ folder (e.g. "en" loads lang/en.yml).
language: en

# All home settings.
home:
  enable: true
  teleport-delay: 2
  max-homes: 3

# All tpa settings.
tpa:
  enable: true
  # Time in seconds before a tpa request expires.
  request-expire-time: 30
  # Time in seconds before the player gets tped.
  teleport-delay: 2

# All warp settings.
warp:
  enable: true
  teleport-delay: 2
  spawn-delay: 0

# All player-warp settings.
pwarp:
  enable: true
  teleport-delay: 2
  max-warps: 3
```

## Language Files

Language files live in `plugins/SystemHomes/lang/`. The bundled `en.yml` is used as the base, and when the plugin starts it will only append any new keys that were added in a newer version.

## Permissions

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
| `systemhomes.player.pwarp`   | Grants access to use player warps.                    |

The command permissions used internally are `systemhomes.cmd.home`, `systemhomes.cmd.tpa`, `systemhomes.cmd.warp`, and `systemhomes.cmd.pwarp`. The `systemhomes.player.*` aliases above grant those command nodes automatically.

## Legacy Import

If a `SystemHomes` folder exists next to the new plugin data folder, the plugin will import:

- homes from `homes.yml`
- warps from `warps.yml`
- player warps from `playerwarps.yml`

Imported data is written into the new storage layout automatically.

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
