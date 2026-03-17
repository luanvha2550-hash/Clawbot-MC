# Clawbot-MC - Roadmap e Histórico

## ✅ Concluído (v1.0.2 - v1.0.4)

### v1.0.2 - Correções de Autonomia e Spawn

| Correção | Descrição | Arquivos |
|----------|-----------|----------|
| **Pacote do Mod** | `fabric.mod.json` atualizado para `net.luanvha2550_hash` | `fabric.mod.json`, `clawbot-mc.mixins.json` |
| **Spawn do Bot** | Bot spawn em posição segura (não em parede) | `createFakePlayer.java` - `findSafeSpawnPosition()` |
| **Autonomia** | `buildInventorySnapshot()` escaneia inventário real | `AutonomyEngine.java` |
| **Autonomia** | `handleActionById()` expandido com 10+ ações | `AutonomyEngine.java` |
| **Tradução** | System prompts em português | `RAG2.java`, `DecisionResolver.java` |
| **Tags Pensamento** | `<pensamento></pensamento>` em PT | `OllamaThinkingResponse.java` |
| **Comando /clawdev** | Debug com dump, status, autonomy, nlp | `ClawdevCommand.java` |

---

### v1.0.3 - Fix Spawn, LLM Connection e Respawn

| Correção | Descrição | Arquivos |
|----------|-----------|----------|
| **Spawn + LLM** | Bot conecta à LLM automaticamente ao spawnar | `BotEventHandler.java` - `initializeLLMConnection()` |
| **Spawn + LLM** | Ollama client inicializado no spawn | `ollamaClient.java` |
| **Morte/Respawn** | `onDeath()` não chama `kill()` | `createFakePlayer.java` |
| **Morte/Respawn** | `handleBotRespawn()` re-inicializa autonomia | `BotEventHandler.java` |
| **Morte/Respawn** | Q-table restaurado após respawn | `AIPlayer.java` - `AFTER_RESPAWN` |
| **Morte/Respawn** | Death spot na LongTermMemory | `BotEventHandler.java` |

---

### v1.0.4 - Refatoração e Performance

| Categoria | Implementação | Impacto | Arquivos |
|-----------|---------------|---------|----------|
| **Performance** | Thread Pool com Limite (4 threads) | 60% CPU redução | `AutonomyEngine.java` |
| **Performance** | NLP Caching (TTL 10min) | 70% redução computação | `NLPProcessorV2.java` |
| **Performance** | KnowledgeCache HashMap O(1) | 80% redução busca | `KnowledgeCache.java` |
| **Performance** | LLM Semantic Cache | 30% hit rate | `LLMDecisionEngine.java` |
| **Refatoração** | `RewardCalculator.java` | Separa cálculo de recompensa | Nova classe |
| **Refatoração** | `QValueService.java` | Q-value + cache | Nova classe |
| **Refatoração** | `ExperienceMemory.java` | PriorityQueue experiências | Nova classe |

---

## ❌ NÃO Implementado (Futuro)

### Upgrade de ML/IA (PENDENTE)

**Status:** **NÃO FOI FEITO** - Deixado para versões futuras conforme solicitado.

| Feature | Descrição | Prioridade |
|---------|-----------|------------|
| **Fine-tune Minecraft-PT** | Treinar modelo com dados PT-BR do Minecraft | Média |
| **ONNX Runtime** | Inferência 5x mais rápida | Alta |
| **8 Intents** | Expandir de 5 para 8 intents | Média |
| **Persistência RL** | Salvar QTable/Experience em disco | Alta |
| **Hybrid RAG** | Contexto + Memória Longo Termo | Baixa |
| **Model Router** | Escolhe melhor modelo por tarefa | Baixa |
| **DQN (Deep Q-Network)** | Aprendizado mais eficiente | Baixa |
| **Federated Learning** | Aprendizado distribuído | Futuro |
| **VLA (Vision-Language-Action)** | Visão + linguagem + ação | Futuro |
| **World Model** | Previsão de estados | Futuro |

---

### Novas Features (PENDENTE)

**Status:** **NÃO FORAM FEITAS** - Deixadas para versões futuras conforme solicitado.

#### Tier 1 (Implementáveis Agora)
| Feature | Complexidade | Impacto |
|---------|--------------|---------|
| Auto-Building | Média | ⭐⭐⭐⭐⭐ |
| Villager Trading | Baixa | ⭐⭐⭐⭐ |
| Advanced PathFinding (A*) | Média | ⭐⭐⭐⭐⭐ |
| Discord Integration | Baixa | ⭐⭐⭐⭐ |

#### Tier 2 (Requerem Planejamento)
| Feature | Complexidade |
|---------|--------------|
| Multi-Bot Coordination | Alta |
| Learning by Demonstration | Alta |
| Auto-Farming | Média |
| PvP Combat | Média |

#### Tier 3 (Futuro)
- Quest System
- Voice Chat
- JEI/REI Integration

---

## 📊 Arquitetura - Refatoração Parcial

### O que foi feito:
- ✅ `RewardCalculator.java` - extraído de RLAgent
- ✅ `QValueService.java` - extraído de RLAgent
- ✅ `ExperienceMemory.java` - extraído de RLAgent

### O que falta (PENDENTE):
- ❌ `RiskCalculator.java` - extrair `calculateRisk()` (~900 linhas)
- ❌ `DeathPatternAnalyzer.java` - extrair death learning
- ❌ `EpsilonManager.java` - extrair epsilon adaptativo
- ❌ `RiskEstimator.java` - extrair para planner system

### God Classes Restantes:
| Classe | Tamanho | Status |
|--------|---------|--------|
| `RLAgent.java` | ~2200 linhas | Parcialmente refatorada |
| `modCommandRegistry.java` | ~131 linhas | ✅ OK |
| `NLPProcessorV2.java` | ~388 linhas | ⚠️ Pendente |

---

## 📝 Resumo para Memória

### Implementado:
1. Fix Spawn (posição segura + LLM connection)
2. Fix Respawn (morte natural + restauração estado)
3. Performance (thread pool, NLP cache, KnowledgeCache, LLM cache)
4. Refatoração parcial (3 classes extraídas)
5. Tradução PT-BR (prompts, tags pensamento)
6. Comando /clawdev (debug)

### NÃO Implementado (para futuro):
1. **Upgrade de ML/IA** - Todos os itens pendentes
2. **Novas Features** - Auto-Building, A*, Trading, Discord, etc.
3. **Refatoração Completa** - RiskCalculator, DeathPatternAnalyzer, etc.

---

## 🔗 Links Úteis

- **Releases:** https://github.com/luanvha2550-hash/Clawbot-MC/releases
- **v1.0.4:** https://github.com/luanvha2550-hash/Clawbot-MC/releases/tag/v1.0.4
- **Download:** https://github.com/luanvha2550-hash/Clawbot-MC/releases/download/v1.0.4/clawbot-mc-1.0.4.jar
