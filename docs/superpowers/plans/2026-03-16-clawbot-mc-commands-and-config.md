# Clawbot-MC Commands and Config Enhancement Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Remove system argument dependency, simplify commands, and update config screens for Clawbot-MC

**Architecture:** Replace System.getProperty("aiplayer.llmMode") with ManualConfig-based provider selection, simplify UI to only show Gemini key for embedding and Ollama model selection

**Tech Stack:** Minecraft Fabric API, Java 21, JSON configuration

---

## Overview

This plan addresses three main issues:
1. **Remove -Daiplayer.llmMode dependency** - Use internal config instead of JVM argument
2. **Simplify commands** - Keep only essential commands
3. **Update Config Screens** - Focus on Gemini key (embedding) and Ollama model selection

---

## Chunk 1: Remove System Property Dependency

### Task 1: Update ManualConfig.java to store provider preference

**Files:**
- Modify: `src/main/java/net/shasankp000/FilingSystem/ManualConfig.java`

- [ ] **Step 1: Add provider field to ManualConfig**

Find the class field declarations (around line 36) and add a new field:

```java
// Add after llmMode field
private String selectedProvider = "ollama"; // Default provider
```

- [ ] **Step 2: Add getter and setter for selectedProvider**

Add these methods after the existing getters:

```java
public String getSelectedProvider() {
    return selectedProvider;
}

public void setSelectedProvider(String provider) {
    this.selectedProvider = provider;
    save();
}
```

- [ ] **Step 3: Load provider from config file**

Find where JSON is loaded (around line 197) and add:

```java
// In the JSON loading section, after loading llmMode
this.selectedProvider = jsonNode.has("selectedProvider")
    ? jsonNode.get("selectedProvider").asText()
    : "ollama";
```

- [ ] **Step 4: Save provider to config file**

Find where JSON is saved and add:

```java
// In the JSON saving section
objectMapper.writeValue(writer, new ObjectMapper()
    .createObjectNode()
    .put("llmMode", llmMode)
    .put("selectedProvider", selectedProvider)
    // ... other fields
);
```

- [ ] **Step 5: Commit**

```bash
git add src/main/java/net/shasankp000/FilingSystem/ManualConfig.java
git commit -m "feat(config): add selectedProvider field to ManualConfig"
```

---

### Task 2: Replace System.getProperty calls with config lookup

**Files:**
- Modify: `src/main/java/net/shasankp000/AIPlayer.java:63`
- Modify: `src/main/java/net/shhasankp000/AIPlayerClient.java:107`
- Modify: `src/main/java/net/shasankp000/Commands/modCommandRegistry.java:1004`
- Modify: `src/main/java/net/shasankp000/AIProviders/EmbeddingProviderFactory.java:26`
- Modify: `src/main/java/net/shasankp000/WebSearch/WebSearchTool.java:35`
- Modify: `src/main/java/net/shasankp000/ChatUtils/DecisionResolver/DecisionResolver.java:74`
- Modify: `src/main/java/net/shhasankp000/GraphicalUserInterface/APIKeysScreen.java:136`
- Modify: `src/main/java/net/shhasankp000/FilingSystem/EmbeddingClientFactory.java:27,247`
- Modify: `src/main/java/net/shasankp000/GameAI/planner/GoalMapper.java:108`

- [ ] **Step 1: Create helper method in ManualConfig**

Add this static method to ManualConfig.java:

```java
public static String getActiveProvider() {
    ManualConfig config = AIPlayer.CONFIG;
    // If config exists and has custom provider set, use it
    if (config != null && config.getSelectedProvider() != null) {
        return config.getSelectedProvider();
    }
    // Fallback to system property for backward compatibility
    return System.getProperty("clawbot.llmMode",
        System.getProperty("aiplayer.llmMode", "ollama"));
}
```

- [ ] **Step 2: Replace System.getProperty in AIPlayer.java**

Replace line 63:
```java
// Before:
String llmProvider = System.getProperty("aiplayer.llmMode", "ollama");

// After:
String llmProvider = ManualConfig.getActiveProvider();
```

- [ ] **Step 3: Replace in AIPlayerClient.java**

Replace line 107 with same pattern.

- [ ] **Step 4: Replace in other files**

Repeat for all other files listed. Replace:
```java
System.getProperty("aiplayer.llmMode", "ollama")
```
with:
```java
ManualConfig.getActiveProvider()
```

- [ ] **Step 5: Commit**

```bash
git add src/main/java/net/shasankp000/AIPlayer.java
git add src/main/java/net/shasankp000/AIPlayerClient.java
git add src/main/java/net/shasankp000/Commands/modCommandRegistry.java
git add src/main/java/net/shasankp000/AIProviders/EmbeddingProviderFactory.java
git add src/main/java/net/shasankp000/WebSearch/WebSearchTool.java
git add src/main/java/net/shasankp000/ChatUtils/DecisionResolver/DecisionResolver.java
git add src/main/java/net/shasankp000/GraphicalUserInterface/APIKeysScreen.java
git add src/main/java/net/shasankp000/FilingSystem/EmbeddingClientFactory.java
git add src/main/java/net/shasankp000/GameAI/planner/GoalMapper.java
git commit -m "refactor: replace System.getProperty with ManualConfig provider lookup"
```

---

## Chunk 2: Simplify Commands

### Task 3: Review and clean up configCommand.java

**Files:**
- Modify: `src/main/java/net/shasankp000/Commands/configCommand.java`

- [ ] **Step 1: Read full configCommand.java to identify all commands**

The file is large (~97KB). Identify which sub-commands are essential:
- Essential: `aiplayer spawn`, `aiplayer remove`
- Review: Other commands

- [ ] **Step 2: Keep only essential commands**

Keep these commands:
- `/aiplayer spawn` - Spawn the bot
- `/aiplayer remove` - Remove the bot

Consider removing or commenting out:
- Debug/test commands
- Export/import commands (unless critical)
- Threat debug commands

- [ ] **Step 3: Commit**

```bash
git add src/main/java/net/shasankp000/Commands/configCommand.java
git commit -refactor(commands): simplify aiplayer commands to essential only"
```

---

## Chunk 3: Update ConfigManager Screen

### Task 4: Simplify ConfigManager.java main screen

**Files:**
- Modify: `src/main/java/net/shasankp000/GraphicalUserInterface/ConfigManager.java`

- [ ] **Step 1: Remove search field and model dropdown**

The current screen has search + dropdown for model selection. Simplify to:
- Just show current selected provider
- Button to open Ollama settings

```java
// In init() method, replace the search/dropdown section with:

// Provider display
int centerX = this.width / 2;
int topMargin = 50;

// Current provider label
Text providerLabel = Text.of("Provider: " + AIPlayer.CONFIG.getSelectedProvider());
this.addDrawable(new TextRendererhelper(providerLabel, centerX - 50, topMargin));
```

- [ ] **Step 2: Simplify bottom buttons**

Reduce from 5 buttons to 3:
- API Keys (goes to screen with Gemini key)
- Save
- Close

Remove:
- Reasoning Log
- Refresh Models (can be automatic)

- [ ] **Step 3: Commit**

```bash
git add src/main/java/net/shasankp000/GraphicalUserInterface/ConfigManager.java
git commit -feat(config): simplify ConfigManager screen"
```

---

## Chunk 4: Update APIKeysScreen

### Task 5: Simplify APIKeysScreen to only Gemini + Ollama

**Files:**
- Modify: `src/main/java/net/shasankp000/GraphicalUserInterface/APIKeysScreen.java`

- [ ] **Step 1: Keep only Gemini Key field**

Remove fields for:
- OpenAI Key
- Claude Key
- Grok Key
- Custom API URL
- Custom API Key

Keep only:
- Gemini Key (for embedding)

- [ ] **Step 2: Add Ollama model selection**

Add a dropdown or text field for Ollama model selection:

```java
private TextFieldWidget ollamaModelField;

// In init():
this.ollamaModelField = new TextFieldWidget(this.textRenderer, startX, startY + 30, fieldWidth, fieldHeight, Text.empty());
this.ollamaModelField.setMaxLength(64);
this.ollamaModelField.setText(AIPlayer.CONFIG.getOllamaModel());
this.addDrawableChild(this.ollamaModelField);
```

- [ ] **Step 3: Add Ollama URL field**

```java
private TextFieldWidget ollamaUrlField;

// In init():
this.ollamaUrlField = new TextFieldWidget(this.textRenderer, startX, startY + 60, fieldWidth, fieldHeight, Text.empty());
this.ollamaUrlField.setMaxLength(256);
this.ollamaUrlField.setText(AIPlayer.CONFIG.getOllamaUrl());
this.addDrawableChild(this.ollamaUrlField);
```

- [ ] **Step 4: Update save method**

```java
private void saveToFile() {
    AIPlayer.CONFIG.setGeminiKey(this.geminiKeyField.getText());
    AIPlayer.CONFIG.setOllamaModel(this.ollamaModelField.getText());
    AIPlayer.CONFIG.setOllamaUrl(this.ollamaUrlField.getText());
    AIPlayer.CONFIG.save();
    configNetworkManager.sendConfigToServer(AIPlayer.CONFIG);
}
```

- [ ] **Step 5: Add fields to ManualConfig**

Add to ManualConfig.java:
```java
private String ollamaModel = "llama3.2";
private String ollamaUrl = "http://localhost:11434";

public String getOllamaModel() { return ollamaModel; }
public void setOllamaModel(String model) { this.ollamaModel = model; }
public String getOllamaUrl() { return ollamaUrl; }
public void setOllamaUrl(String url) { this.ollamaUrl = url; }
```

- [ ] **Step 6: Commit**

```bash
git add src/main/java/net/shasankp000/GraphicalUserInterface/APIKeysScreen.java
git add src/main/java/net/shasankp000/FilingSystem/ManualConfig.java
git commit -feat(config): simplify APIKeysScreen to Gemini + Ollama only"
```

---

## Chunk 5: Final Integration

### Task 6: Update modCommandRegistry command name

**Files:**
- Modify: `src/main/java/net/shasankp000/Commands/modCommandRegistry.java:14`
- Modify: `src/main/java/net/shasankp000/Commands/configCommand.java:14`

- [ ] **Step 1: Change command from /configMan to /clawbot**

In modCommandRegistry.java line 14:
```java
// Before:
dispatcher.register(CommandManager.literal("configMan")

// After:
dispatcher.register(CommandManager.literal("clawbot")
```

In configCommand.java line 14:
```java
// Before:
dispatcher.register(CommandManager.literal("configMan")

// After:
dispatcher.register(CommandManager.literal("clawbot")
```

- [ ] **Step 2: Commit**

```bash
git add src/main/java/net/shasankp000/Commands/modCommandRegistry.java
git add src/main/java/net/shasankp000/Commands/configCommand.java
git commit -refactor: rename /configMan to /clawbot"
```

---

### Task 7: Build and verify

**Files:**
- Build verification

- [ ] **Step 1: Run gradle build**

```bash
cd "D:/Users/luanv/OneDrive/Área de Trabalho/GAMES/Trabalhos/mod-mine-ai"
./gradlew build
```

- [ ] **Step 2: Verify JAR is created**

Check `build/libs/` for the new JAR file.

- [ ] **Step 3: Commit all changes**

```bash
git add .
git commit -m "feat: remove system property dependency, simplify commands and config screens"
```

---

## Summary of Changes

| File | Change |
|------|--------|
| ManualConfig.java | Add selectedProvider, ollamaModel, ollamaUrl fields |
| AIPlayer.java | Replace System.getProperty with ManualConfig.getActiveProvider() |
| AIPlayerClient.java | Same replacement |
| modCommandRegistry.java | Replace System.getProperty, rename command to /clawbot |
| configCommand.java | Rename command to /clawbot, simplify |
| APIKeysScreen.java | Keep only Gemini Key + Ollama URL/Model |
| ConfigManager.java | Simplify UI, remove model dropdown |
| 8 other files | Replace System.getProperty calls |

---

## After Implementation

After completing all tasks:
1. Test that `/clawbot` opens the config screen
2. Test that Gemini API key can be saved
3. Test that Ollama URL and model can be configured
4. Verify bot spawns without -Daiplayer.llmMode argument
5. Verify no crashes on startup

---

**Plan complete and saved to `docs/superpowers/plans/2026-03-16-clawbot-mc-commands-and-config.md`. Ready to execute?**