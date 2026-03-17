# Especificação de Melhoria da IA do Clawbot-MC

**Data:** 2026-03-16
**Versão:** 1.0
**Status:** Rascunho para Revisão

---

## 1. Visão Geral

Este documento descreve a proposta de melhoria do sistema de inteligência artificial do mod Clawbot-MC (anteriormente AI-Player). O objetivo é tornar o bot mais autônomo, mais inteligente no aprendizado e mais natural na conversa com o jogador, mantendo uma arquitetura híbrida que não dependa 100% de serviços de nuvem.

### Objetivos Principais

1. **Autonomia Aprimorada** - Bot toma iniciativas próprias sem comandos explícitos
2. **Aprendizado Eficiente** - Aprende com menos rodadas usando estados mais ricos
3. **Conversa Natural** - Respostas mais fluídas em português brasileiro

### Escopo

- Substituição do sistema de NLP local (BERT + CART + LIDSNet)
- Melhoria do agente de aprendizado por reforço (RLAgent)
- Otimização da integração com LLM
- Adição de novas intents para comandos complexos

---

## 2. Arquitetura Atual vs Nova

### 2.1 Arquitetura Atual

```
┌─────────────────────────────────────────────────────────────────┐
│                    ARQUITETURA ATUAL                            │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  Jogador → NLPProcessor (3 modelos) → Intent                   │
│                                              │                  │
│                                              ▼                  │
│                                  LLM (fallback quandoincerteza) │
│                                              │                  │
│                                              ▼                  │
│                                    RLAgent (Q-Learningbásico)    │
│                                              │                  │
│                                              ▼                  │
│                                   AutonomyEngine (5 layers)      │
│                                                                 │
│  Modelos locais: ~100MB                                        │
│  Dependências: DJL, OpenNLP                                     │
│  Chamadas LLM: 100% (como fallback)                            │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

### 2.2 Nova Arquitetura Proposta

```
┌─────────────────────────────────────────────────────────────────┐
│                    NOVA ARQUITETURA                            │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  Jogador → NLPv2 (1 modelo) → Intent                           │
│                          │                                      │
│              ┌───────────┴───────────┐                         │
│              ▼                       ▼                          │
│      Simple Action              Complex Action                  │
│      (executa direto)           (chama LLM                      │
│                                   se confiança < 60%)           │
│                                              │                  │
│                                              ▼                  │
│                                    RLAgent v2                    │
│                                    (estado rico +                │
│                                     priority queue)              │
│                                              │                  │
│                                              ▼                  │
│                                   AutonomyEngine (5 layers)      │
│                                                                 │
│  Modelos locais: ~80MB                                        │
│  Dependências: sentence-transformers (somente)                │
│  Chamadas LLM: ~30% (só quando necessário)                    │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

---

## 3. Sistema de NLP Local (NLPv2)

### 3.1 Substituição dos Modelos

| Modelo Atual | Novo Modelo | Tamanho | Idiomas |
|--------------|-------------|---------|---------|
| DistilBERT (66MB) | sentence-transformers | 80MB | 50+ |
| CART (árvore) | all-MiniLM-L6-v2 | (incluído) | incl. pt |
| LIDSNet (rede neural) | (removido) | - | - |

### 3.2 Justificativa

**Problemas dos modelos atuais:**
- Não foram treinados em português brasileiro
- DecisionResolver combina 3 modelos com lógica básica
- LIDSNet depende de OpenNLP (biblioteca antiga)
- Múltiplas inferências por mensagem (lento)

**Vantagens do novo modelo:**
- all-MiniLM-L6-v2 já inclui suporte a português
- Embedding único e rápido (~10ms)
- Apenas uma dependência (sentence-transformers)
- 3x mais rápido que a combinação atual

### 3.3 Classifier de Intenção

O novo sistema terá as seguintes intents:

```java
public enum Intent {
    REQUEST_ACTION,      // "mine diamond", "venha cá"
    ASK_INFORMATION,    // "onde está o ferro?", "que horas são?"
    GENERAL_CONVERSATION, // "oi", "bom dia", "legal"
    COMPLEX_ACTION,     // "construa uma casa" (NOVO!)
    UNSPECIFIED         // mensagem ambígua
}
```

A intent `COMPLEX_ACTION` é novidade e permite detectar comandos que precisam de mais processamento pelo LLM.

---

## 4. Integração com LLM

### 4.1 Estratégia Híbrida

O LLM não será mais usado como fallback apenas, mas como recurso estratégico:

```java
public class LLMDecisionEngine {

    // Só chama LLM nestas situações:
    public boolean shouldUseLLM(Intent intent, double confidence) {
        return intent == COMPLEX_ACTION && confidence < 0.6
            || intent == ASK_INFORMATION && confidence < 0.5
            || isOngoingConversation();
    }

    // Prompts em português brasileiro
    private String buildActionPrompt(String userMessage) {
        return "Você é um agente de IA em Minecraft..."; //详见 abaixo
    }
}
```

### 4.2 Prompts em Português

**Prompt para ação detalhada:**
```
Você é um agente de IA em Minecraft chamado Clawbot-MC.

O jogador pediu: "{mensagem}"

1. Identifique a ação principal
2. Identifique objetos/alvos específicos
3. Liste os passos necessários
4. Considere recursos necessários

Responda em JSON com formato:
{
  "ação": "construir_casa",
  "detalhes": {
    "material": "madeira",
    "tamanho": "pequeno",
    "local": "perto_do_jogador"
  },
  "passos": ["coletar_madeira", "craftar_pranchas", "construir"]
}
```

**Prompt para conversa natural:**
```
Você é o Clawbot-MC, um companheiro de Minecraft amigável e prestativo.

Histórico recente:
{últimas 3 mensagens}

Responda de forma natural, em português brasileiro, mantendo o contexto da conversa.
```

### 4.3 Redução de Custos

| Cenário | Antes | Depois | Economia |
|---------|-------|--------|----------|
| Comando simples ("mine") | LLM (se confiança baixa) | 0% | ~70% |
| Comando complexo | LLM | 100% | 0% |
| Conversa | LLM sempre | 50% | 50% |
| **Total** | ~100% chamadas | ~30% | **70%** |

---

## 5. Agente de Aprendizado (RLAgent v2)

### 5.1 Q-Learning com Estado Rico

**Estado atual (4 features):**
```json
{
  "saúde": 10,
  "fome": 14,
  "mobs_perto": 2,
  "tem_diamante": false
}
```

**Novo estado (12 features):**
```json
{
  "saúde": 10,
  "fome": 14,
  "mobs_perto": 2,
  "mobs_atacando": 1,
  "distancia_owner": 8,
  "inventario_cheio": false,
  "tem_ferramenta": true,
  "bloco_alvo_perto": true,
  "hora_dia": 0.5,
  "perigo_local": "low",
  "ultima_acao": "minerar",
  "ultima_recompensa": 50
}
```

### 5.2 Priority Queue de Experiência

```java
public class RLAgentV2 {

    // Memória de experiências (em vez de só última)
    private PriorityQueue<Experience> experienceMemory;

    // Seleção de experiência para aprender
    private Experience selectExperience() {
        // Prioriza experiências com alta recompensa
        // mas também inclui algumas aleatórias
        return experienceMemory.poll();
    }
}
```

### 5.3 Epsilon Adaptativo

```java
// Antes: epsilon fixo em 0.1
epsilon = 0.1;

// Depois: epsilon que adapta
if (rewardsAreHigh) {
    epsilon = Math.max(MIN_EPSILON, epsilon * 0.95); // Explora menos
} else {
    epsilon = Math.min(MAX_EPSILON, epsilon * 1.05); // Explora mais
}
```

### 5.4 Recompensas Contextuais

| Ação | Recompensa Base | Bônus Contextual |
|------|-----------------|------------------|
| Matar mob | +10 | +5 se mobs atacando |
| Coletar recurso | +20 | +10 se inventário vazio |
| Encontrar diamante | +50 | +100 primeira vez |
| Construir | +30 | +20 se perto do owner |
| Levar dano | -15 | -10 se saúde baixa |
| Morrer | -100 | - |

---

## 6. Fluxo de Execução Completo

```
Jogador: "construa uma casa pra mim"

PASSO 1: NLP Local (sentence-transformers)
├── Input: "construa uma casa pra mim"
├── Embedding: vetor de 384 dimensões
├── Classifier: COMPLEX_ACTION, confidence=0.72
└── Tempo: ~15ms

PASSO 2: Decisão LLM
├── Intent=COMPLEX_ACTION + confiança>60%
├── Prompt gerado em português
├── LLM retorna: {ação: "construir_casa", ...}
└── Custo: ~100 tokens

PASSO 3: RLAgent
├── Estado atual captado
├── Ação adicionada à priority queue
├── Q-value atualizado
└── Aprendizado: Q[estado][ação] += α * (reward + γ*max - Q)

PASSO 4: AutonomyEngine
├── Layers avaliam (Survival, Combat, Goals, Command, Idle)
├── CommandLayer executa ação
└── Funções chamadas via FunctionCaller

PASSO 5: Feedback Loop
├── Ação concluída ou falhou
├── Recompensa calculada
├── Q-table atualizada
└── Persistida em SQLite
```

---

## 7. Requisitos de Recursos

### 7.1 Comparação

| Recurso | Antes | Depois | Diferença |
|---------|-------|--------|-----------|
| **RAM total** | ~500MB | ~420MB | -80MB |
| **CPU/tick** | Alto | Médio | Melhor |
| **Download modelos** | ~100MB | ~80MB | -20MB |
| **Chamadas LLM** | 100% | ~30% | -70% |
| **Init time** | ~10s | ~8s | Melhor |
| **GPU necessária** | Não | Não | - |

### 7.2 Especificações Mínimas

- Processador: Intel i5 (ou equivalente)
- RAM: 8GB (12GB recomendado)
- Espaço em disco: 500MB extras
- Internet: 10MB/s (para LLM)

---

## 8. Riscos e Mitigações

| Risco | Probabilidade | Mitigação |
|-------|---------------|-----------|
| Modelo não funciona bem em português | Baixa | Usar modelo que já suporte pt (all-MiniLM-L6-v2 tem) |
| Q-Learning piora sem GPU | Baixa | Não usamos DQN, só Q melhorado (funciona em CPU) |
| Breaking changes no código | Média | Interfaces mantêm compatibilidade |
| LLM muito lento | Baixa | Cache de respostas comuns |
| Performance degradada | Baixa | Benchmarks após cada mudança |

---

## 9. Plano de Implementação

### Fase 1: NLPv2 (Semana 1)
1. Adicionar dependência sentence-transformers
2. Implementar novo NLPProcessor
3. Testar com mensagens em português
4. Validar latência < 20ms

### Fase 2: Integração LLM (Semana 2)
1. Implementar LLMDecisionEngine
2. Criar novos prompts em português
3. Adicionar sistema de cache
4. Testar redução de chamadas

### Fase 3: RLAgent v2 (Semana 3)
1. Expandir estado para 12 features
2. Implementar priority queue
3. Adicionar epsilon adaptativo
4. Testar aprendizado em 100 ações

### Fase 4: Integração Final (Semana 4)
1. Conectar todos os componentes
2. Testes de integração
3. Benchmarks de performance
4. Documentação

---

## 10. Critérios de Sucesso

- [ ] NLP processa mensagens em < 20ms
- [ ] 70% de redução em chamadas LLM
- [ ] RLAgent aprende comportamento em < 50 ações
- [ ] Bot responde a comandos em português naturalmente
- [ ] RAM total < 500MB
- [ ] Sem dependência de GPU

---

## 11. Referências

- sentence-transformers: https://sbert.net/
- all-MiniLM-L6-v2: https://huggingface.co/sentence-transformers/all-MiniLM-L6-v2
- Q-Learning: https://en.wikipedia.org/wiki/Q-learning
- Minecraft Fabric API: https://fabricmc.net/

---

**Documento criado para revisão**
**Precisa de aprovação do usuário antes de implementar**