# Vaadin AI Chat

Chat web con interfaccia [Vaadin](https://vaadin.com/) e backend [Spring AI](https://spring.io/projects/spring-ai), configurato per usare [Cerebras](https://cerebras.ai/) come provider LLM tramite API OpenAI-compatibile.

## Stack

| Componente | Versione |
|---|---|
| Java | 17 |
| Spring Boot | 3.4.10 |
| Spring AI | 1.0.6 |
| Vaadin | 24.7.1 |

## Funzionalità

- Interfaccia chat in tempo reale (Server Push via WebSocket)
- Memoria della conversazione in-memory (finestra scorrevole)
- Sistema prompt configurabile (default: risponde sempre in italiano)
- Compatibile con qualsiasi endpoint OpenAI-compatibile (Cerebras, OpenAI, Ollama...)

## Configurazione

Crea un file `.env` nella root del progetto (mai committare questo file):

```
CEREBRAS_API_KEY=la_tua_chiave
```

Le variabili del `.env` vengono caricate automaticamente da [spring-dotenv](https://github.com/paulschwarz/spring-dotenv) e sono disponibili in `application.properties`.

### `application.properties`

```properties
server.port=8095

# Cerebras (endpoint OpenAI-compatibile)
spring.ai.openai.base-url=https://api.cerebras.ai
spring.ai.openai.api-key=${CEREBRAS_API_KEY}
spring.ai.openai.chat.options.model=gpt-oss-120b
spring.ai.openai.chat.options.max-tokens=32768
```

### Usare Ollama invece di Cerebras

Commenta le righe Cerebras e decommenta nel `pom.xml` la dipendenza `spring-ai-starter-model-ollama`, poi in `application.properties`:

```properties
spring.ai.ollama.base-url=http://localhost:11434
spring.ai.ollama.chat.options.model=gemma4:e2b
```

## Avvio

```bash
cd chat-vaadin
mvn spring-boot:run
```

Apri il browser su [http://localhost:8095](http://localhost:8095).

## Note tecniche

### Fix HTTP 411 (Content-Length required)

Cerebras richiede l'header `Content-Length` nella richiesta HTTP. Spring AI usa di default il chunked transfer encoding che non lo include. Il bean `restClientBuilder()` in `ChatApplication` configura `SimpleClientHttpRequestFactory` con `bufferRequestBody=true` per risolvere questo problema.

### Sicurezza

Il file `.env` è escluso dal repository tramite `.gitignore`. Non committare mai chiavi API nel codice sorgente.