# Vaadin AI Chat

Chat web con interfaccia [Vaadin](https://vaadin.com/) e backend [Spring AI](https://spring.io/projects/spring-ai).  
Il provider LLM è selezionabile tramite **profili Spring** e, per default, le richieste passano attraverso [LiteLLM](https://docs.litellm.ai/), un proxy OpenAI-compatibile che supporta 100+ provider (Cerebras, OpenRouter, OpenAI, Ollama, ecc.) con un'unica interfaccia.

## Stack

| Componente | Versione |
|---|---|
| Java | 17 |
| Spring Boot | 3.4.10 |
| Spring AI | 1.0.6 |
| Vaadin | 24.7.1 |
| LiteLLM | main-stable (Docker) |

## Funzionalità

- Interfaccia chat in tempo reale (Server Push via WebSocket)
- Memoria della conversazione in-memory (finestra scorrevole)
- Sistema prompt configurabile (default: risponde sempre in italiano)
- Supporto multi-provider tramite LiteLLM o connessione diretta

## Architettura con LiteLLM

```
Browser → Vaadin (8095) → Spring AI → LiteLLM proxy (4000) → Cerebras / OpenRouter / OpenAI / Ollama
```

LiteLLM espone un'API OpenAI-compatibile su `http://localhost:4000`. Spring AI la usa come se fosse OpenAI, senza sapere nulla del provider sottostante. Questo elimina ogni integrazione specifica per provider e il fix HTTP 411 non è più necessario.

## Configurazione

### 1. File `.env` (root del progetto)

Crea `.env` nella root (mai committare questo file):

```env
CEREBRAS_API_KEY=la_tua_chiave
OPENROUTER_API_KEY=la_tua_chiave   # opzionale
OPENAI_API_KEY=la_tua_chiave       # opzionale
```

Le variabili vengono caricate automaticamente da [spring-dotenv](https://github.com/paulschwarz/spring-dotenv).

### 2. Profilo attivo

In `application.properties` scegli il profilo:

```properties
# litellm | openrouter | openai | ollama
spring.profiles.active=litellm
```

| Profilo | Provider | File properties |
|---|---|---|
| `litellm` | LiteLLM proxy locale (default) | `application-litellm.properties` |
| `openrouter` | OpenRouter diretto | `application-openrouter.properties` |
| `openai` | Cerebras via API OpenAI-compatibile | `application-openai.properties` |
| `ollama` | Ollama locale | `application-ollama.properties` |

---

## LiteLLM — setup e configurazione

LiteLLM gira in Docker nella directory `litellm/`.

### Struttura

```
litellm/
├── docker-compose.yml   # avvia il container
├── config.yaml          # modelli esposti dal proxy
├── .env                 # chiavi API (non committare)
└── .env.example         # template
```

### Avvio

```bash
cd litellm

# Prima volta: crea il file .env
cp .env.example .env
# Edita .env e inserisci le chiavi API

# Avvia il proxy
docker compose up -d

# Verifica che sia attivo
curl http://localhost:4000/health/liveliness
# → "I'm alive!"
```

### Configurare i modelli (`config.yaml`)

Il file `litellm/config.yaml` definisce i modelli esposti dal proxy. Esempio della configurazione attuale:

```yaml
model_list:
  # Cerebras (veloce, gratuito con API key)
  - model_name: cerebras-gpt-oss-120b
    litellm_params:
      model: cerebras/gpt-oss-120b
      api_key: os.environ/CEREBRAS_API_KEY

  # OpenRouter (accesso a centinaia di modelli)
  - model_name: openrouter-claude
    litellm_params:
      model: openrouter/anthropic/claude-sonnet-4-5
      api_key: os.environ/OPENROUTER_API_KEY

  # Ollama locale
  - model_name: ollama-gemma
    litellm_params:
      model: ollama/gemma4:e2b
      api_base: http://host.docker.internal:11434

general_settings:
  master_key: os.environ/LITELLM_MASTER_KEY
```

Per aggiungere un nuovo modello, aggiungi una voce in `model_list` e riavvia il container:

```bash
docker compose restart
```

### Cambiare modello in Spring AI

Modifica `model` in `application-litellm.properties`:

```properties
spring.ai.openai.chat.options.model=cerebras-gpt-oss-120b
# oppure: openrouter-claude | openrouter-gpt4o | ollama-gemma | gpt-4o
```

### Testare un modello direttamente

```bash
curl -X POST http://localhost:4000/v1/chat/completions \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer sk-litellm-master-key-change-me" \
  -d '{"model":"cerebras-gpt-oss-120b","messages":[{"role":"user","content":"Ciao!"}]}'
```

---

## Avvio dell'applicazione

```bash
# 1. Assicurati che LiteLLM sia in esecuzione
cd litellm && docker compose up -d && cd ..

# 2. Avvia l'app
./mvnw spring-boot:run
```

Apri il browser su [http://localhost:8095](http://localhost:8095).

---

## Note tecniche

### Fix HTTP 411 (Content-Length required)

Alcuni provider OpenAI-compatibili diretti (es. Cerebras senza proxy) richiedono l'header `Content-Length`. Il bean `restClientBuilder()` in `ChatApplication` configura `SimpleClientHttpRequestFactory` con `bufferRequestBody=true`. **Con LiteLLM questo fix non è più necessario**, ma rimane per i profili che si connettono direttamente ai provider.

### Profili e bean Ollama

Spring AI 1.0.6 registra sempre i bean dei provider presenti nel classpath. Per evitare `NoUniqueBeanDefinitionException` (due `ChatModel` attivi), i profili non-Ollama escludono esplicitamente l'autoconfigurazione:

```properties
spring.autoconfigure.exclude=org.springframework.ai.model.ollama.autoconfigure.OllamaChatAutoConfiguration
```

### Sicurezza

I file `.env` sono esclusi dal repository tramite `.gitignore`. Non committare mai chiavi API nel codice sorgente.