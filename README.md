# Clawbot-MC

An intelligent Minecraft companion bot with AI-powered autonomy, combat, and memory systems.

## Features

- 🤖 **AI-Powered Autonomy** - Bot makes decisions autonomously using decision layers
- ⚔️ **Combat System** - Automatically fights hostile mobs
- 🧠 **Memory & Learning** - Q-Learning system that improves over time
- 💬 **Natural Conversation** - Chat with the bot in Portuguese or English
- 🔧 **Full Configuration** - GUI config with API key management

## Requirements

- Minecraft 1.21.1
- Fabric Loader 0.16.10+
- Java 21
- Optional: Carpet Mod 1.4.147+ (for better movement)
- Optional: Ollama running locally (for local LLM)

## Installation

1. Download the latest release from [Releases](https://github.com/luanvha2550-hash/Clawbot-MC/releases)
2. Place the JAR in your Minecraft `mods` folder
3. Start Minecraft with Fabric 1.21.1
4. Use `/clawbot` to open the configuration menu
5. Use `/aiplayer spawn` to spawn the bot

## Configuration

Run `/clawbot` in-game to open the configuration GUI where you can:

- Set your Ollama URL (default: http://localhost:11434)
- Select the Ollama model to use
- Configure Gemini API key for embeddings

## Commands

- `/clawbot` - Opens configuration menu
- `/aiplayer spawn` - Spawns the AI bot
- `/aiplayer remove` - Removes the AI bot

## Language Support

- Portuguese (Brazil) - Full support
- English - Full support

## Supported LLM Providers

- **Ollama** (recommended, runs locally)
- OpenAI
- Anthropic (Claude)
- Google (Gemini)
- xAI (Grok)

## Architecture

The bot uses a layered decision system:

1. **Survival Layer** - Health, hunger, danger detection
2. **Combat Layer** - Hostile mob detection and combat
3. **Goals Layer** - Player-defined objectives
4. **Command Layer** - Player commands
5. **Idle Layer** - Default behaviors (follow, patrol)

## Credits

Created and maintained by [Luanv](https://github.com/luanvha2550-hash)

## License

MIT License