# Central Inteligente — Backend

API Java para receber pedidos em texto livre, estruturá-los via IA e expô-los via REST.  
Interface web no [repositório frontend](https://github.com/and1ssu/IA-DRIVEN-frontend).

---

## Como executar

**Pré-requisito:** Java 17+

```bash
./scripts/run.sh
```

A API sobe em `http://localhost:8080`.

### Com IA ativa (recomendado para avaliação)

```bash
OPENAI_API_KEY=sua_chave ./scripts/run.sh
```

### Forçando uso obrigatório da IA

```bash
OPENAI_API_KEY=sua_chave AI_REQUIRED=true ./scripts/run.sh
```

Com `AI_REQUIRED=true`, qualquer falha na chamada à IA retorna HTTP 500 — nenhum pedido é processado pelo fallback heurístico silenciosamente.

### Variáveis de ambiente

| Variável | Padrão | Descrição |
|---|---|---|
| `PORT` | `8080` | Porta da API |
| `OPENAI_API_KEY` ou `AI_API_KEY` | — | Chave da API (ativa o parser de IA) |
| `AI_BASE_URL` | `https://api.openai.com/v1` | Endpoint OpenAI-compatible |
| `AI_MODEL` | `gpt-4o-mini` | Modelo usado no parsing |
| `AI_REQUIRED` | `false` | Falha se a IA não estiver disponível |

> O projeto é compatível com qualquer provedor OpenAI-compatible (Groq, Together, OpenRouter, etc.) — basta ajustar `AI_BASE_URL` e `AI_MODEL`.

---

## Endpoints

### `POST /pedido`

Recebe um pedido em texto livre e retorna a estrutura extraída.

**Corpo aceito (JSON):**
```json
{ "texto": "Quero 10 caixas de leite integral e 5 fardos de agua para entrega amanha" }
```

**Corpo aceito (texto puro):**
```
Quero 10 caixas de leite integral e 5 fardos de agua para entrega amanha
```

**Resposta `201`:**
```json
{
  "id": 1,
  "cliente": "desconhecido",
  "itens": [
    { "produto": "leite integral", "quantidade": 10, "unidade": "caixa" },
    { "produto": "agua",           "quantidade": 5,  "unidade": "fardo" }
  ],
  "data_entrega": "2026-05-08",
  "texto_original": "Quero 10 caixas de leite integral e 5 fardos de agua para entrega amanha",
  "origem_parser": "ia:gpt-4o-mini",
  "criado_em": "2026-05-07T14:23:01Z"
}
```

**Erros:**

| Status | Quando |
|---|---|
| `400` | Texto vazio ou nenhum item identificado com quantidade |
| `500` | Falha interna ou IA indisponível com `AI_REQUIRED=true` |

---

### `GET /pedidos`

Lista todos os pedidos armazenados em memória.

```bash
curl http://localhost:8080/pedidos
```

---

### `GET /pedido/:id`

Retorna um pedido pelo ID.

```bash
curl http://localhost:8080/pedido/1
```

**Erros:** `404` se o ID não existir, `400` se o ID não for numérico.

---

### `GET /health`

```bash
curl http://localhost:8080/health
# { "status": "ok" }
```

---

## Estrutura do projeto

```
src/main/java/com/centralpedidos/
  Application.java                   # entry point — wiring de dependências
  http/
    ApiServer.java                   # HttpServer, rotas, CORS, serialização
  service/
    PedidoService.java               # regras de negócio e validação
  ai/
    OrderParser.java                 # interface do parser
    OpenAiOrderParser.java           # chamada HTTP à API OpenAI-compatible
    RuleBasedOrderParser.java        # parser heurístico por regex (offline)
    ResilientOrderParser.java        # tenta IA, usa fallback se falhar
    AiConfig.java                    # leitura de variáveis de ambiente
    PedidoEstruturado.java           # record de saída do parser
  model/
    Pedido.java                      # record do pedido completo
    ItemPedido.java                  # record de item (produto, quantidade, unidade)
  repository/
    PedidoRepository.java            # interface do repositório
    InMemoryPedidoRepository.java    # implementação em memória com AtomicInteger
  util/
    Json.java                        # serialização/deserialização JSON sem biblioteca externa
scripts/
  run.sh                             # compila e executa em um comando
```

---

## Decisões técnicas

**Java puro sem framework**  
O servidor HTTP usa `com.sun.net.httpserver.HttpServer`, disponível em qualquer JDK 17+. Não há Spring, Quarkus ou Micronaut. Isso elimina tempo de setup, configuração e download de dependências — basta ter o JDK instalado e rodar `./scripts/run.sh`.

**JSON sem biblioteca externa**  
`Json.java` implementa serialização (objeto → string) e deserialização (string → `Map`) à mão. Evita adicionar Jackson ou Gson como dependência, mantendo zero configuração de build. A deserialização suporta objetos, arrays, strings, números, booleanos e null.

**Interface `OrderParser` com três implementações**  
A separação em interface permite trocar o mecanismo de parsing sem tocar em `PedidoService`:
- `OpenAiOrderParser` — faz a chamada HTTP com `java.net.http.HttpClient`
- `RuleBasedOrderParser` — regex para uso offline ou desenvolvimento sem chave
- `ResilientOrderParser` — orquestra os dois: tenta IA, faz fallback em caso de falha (a menos que `AI_REQUIRED=true`)

**`AI_REQUIRED=true` para avaliação**  
Sem essa flag, o sistema silenciosamente usa o parser heurístico quando a IA falha. Com ela, qualquer falha na IA gera erro visível — garantindo que, em ambiente de avaliação, o sistema use IA de verdade.

**Banco em memória com `AtomicInteger`**  
`InMemoryPedidoRepository` usa `CopyOnWriteArrayList` para a lista e `AtomicInteger` para geração de IDs. Thread-safe sem precisar de `synchronized` explícito. Suficiente para o escopo do desafio.

**CORS aberto**  
O header `Access-Control-Allow-Origin: *` é adicionado em todas as respostas. Sem isso o frontend em `localhost:5173` seria bloqueado pelo navegador ao chamar `localhost:8080`.

---

## Uso de IA

### Ferramenta utilizada

**Claude Code (Anthropic)** — assistente direto no terminal e no editor durante todo o desenvolvimento do projeto.

---

### Arquitetura

**Como usei:** descrevi o problema (API Java sem framework para receber pedido em texto livre, estruturar com IA e expor via REST) e pedi uma proposta de arquitetura.

**O que a IA propôs:**  
A separação em camadas `http / service / ai / model / repository / util` — cada pacote com responsabilidade única. A ideia de abstrair o parser atrás de uma interface (`OrderParser`) também veio da IA, o que permitiu plugar `RuleBasedOrderParser` como fallback sem alterar nenhuma outra camada.

**Onde validei:**  
Revisei se cada classe tinha uma única razão para mudar. `PedidoService` não sabe se o parser usa IA ou regex — só chama `parser.parse()`. `ApiServer` não sabe nada de parsing — só converte HTTP em chamadas de serviço. A separação passou na revisão.

---

### Parsing com IA — prompt engineering

O coração do sistema é o prompt enviado ao modelo. Ele passou por quatro iterações até chegar à versão final.

**Iteração 1 — prompt genérico:**
```
Extraia os itens do pedido abaixo e retorne em JSON.
```
**Problema:** o modelo retornava texto explicativo antes do JSON, ou envolvia o JSON em blocos markdown (` ```json ` ... ` ``` `).  
**Correção:** instrução explícita para responder *apenas* JSON válido sem markdown, e uso de `"response_format": { "type": "json_object" }` na chamada à API para forçar saída estruturada.

**Iteração 2 — produto com unidade misturada:**
```json
{ "produto": "caixas de leite integral", "quantidade": 10 }
```
**Problema:** a unidade de embalagem ficava dentro do nome do produto.  
**Correção:** regra explícita no prompt: *"Separe unidade/logística do produto: '10 caixas de leite integral' vira produto 'leite integral', quantidade 10, unidade 'caixa'."*

**Iteração 3 — datas relativas sem contexto:**  
Textos com "amanhã" ou "depois de amanhã" geravam datas erradas porque o modelo não sabia a data atual.  
**Correção:** a `dataReferencia` (data do servidor no momento da chamada) é enviada no prompt do usuário: `"Data de referencia: 2026-05-07"`. A instrução instrui o modelo a usá-la para resolver termos relativos.

**Iteração 4 — invenção de cliente:**  
O modelo às vezes inventava um nome de cliente quando não havia nenhum no texto.  
**Correção:** regra explícita: *"Não invente cliente, itens ou data. Se o cliente não aparecer no texto, use 'desconhecido'."*

**Prompt final (em produção):**
```
Voce estrutura pedidos comerciais escritos em portugues do Brasil.
Responda apenas com JSON valido, sem markdown, neste formato:
{
  "cliente": "nome do cliente ou desconhecido",
  "itens": [
    { "produto": "produto em minusculas, sem unidade de embalagem", "quantidade": 10, "unidade": "caixa" }
  ],
  "data_entrega": "YYYY-MM-DD ou null"
}

Regras:
- Use a data de referencia enviada pelo usuario para resolver datas relativas como hoje, amanha e depois de amanha.
- Nao invente cliente, itens ou data.
- Separe unidade/logistica do produto: "10 caixas de leite integral" vira produto "leite integral", quantidade 10, unidade "caixa".
- Remova termos de entrega do nome do produto.
- Se a quantidade estiver ausente ou ambigua, nao inclua o item.
```

---

### Parser heurístico (`RuleBasedOrderParser`)

**Como usei a IA:** pedi a geração inicial das expressões regulares para extrair itens, unidades e datas do texto.

**Onde precisei corrigir:**  
A regex inicial de itens não tratava separadores compostos como `" e 5 "` corretamente — parava de capturar antes do segundo item. Refinei o lookahead para reconhecer o padrão `\s+e\s*\d+` como delimitador de item, não como parte do nome do produto.  

O `normalizeUnidade` gerado pela IA pluralizava de volta palavras como `"kg"` ao tentar remover o `"s"` final. Adicionei a exceção `!"kg".equals(normalized)` manualmente após identificar o bug nos testes.

---

### Validação e testes

Após cada ciclo de geração, compilei com `javac` e testei os endpoints manualmente:

```bash
# Compilar
./scripts/run.sh

# Health check
curl -s http://localhost:8080/health

# Criar pedido
curl -s -X POST http://localhost:8080/pedido \
  -H 'Content-Type: application/json' \
  -d '{"texto":"Quero 10 caixas de leite integral e 5 fardos de agua para entrega amanha"}'

# Listar pedidos
curl -s http://localhost:8080/pedidos

# Buscar pedido por ID
curl -s http://localhost:8080/pedido/1

# Testar erro 404
curl -s http://localhost:8080/pedido/999

# Testar payload inválido
curl -s -X POST http://localhost:8080/pedido \
  -H 'Content-Type: application/json' \
  -d '{}'
```

---

### Resumo — o que a IA fez bem e onde precisei corrigir

| Tarefa | IA acertou | Precisei corrigir |
|---|---|---|
| Separação em camadas e interfaces | ✅ Estrutura coerente e extensível | — |
| Chamada HTTP com `java.net.http` | ✅ Código correto na primeira geração | — |
| Prompt de parsing | Estrutura base correta | 4 iterações: markdown, unidade, data relativa, cliente inventado |
| Regex de itens | Reconhecimento básico funcionou | Lookahead para separador `"e N"` precisou de ajuste manual |
| Normalização de unidade | Lógica geral correta | Exceção para `"kg"` adicionada manualmente |
| Serialização JSON manual | ✅ Cobriu os casos necessários | — |
