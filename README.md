# Cyco Collections Service

## 1. Descricao funcional

**Nome do microsservico:** `cyco-collections`

O `cyco-collections` e o microsservico responsavel por gerenciar solicitacoes de coleta de residuos dentro da plataforma Cyco. Ele concentra o ciclo de vida da coleta, desde a criacao da solicitacao pelo gerador ate a selecao do coletor, aceite, cancelamento, conclusao e planejamento de rotas.

Principais responsabilidades:

- Criar solicitacoes de coleta para geradores.
- Buscar, filtrar e consultar coletas por status, identificador, gerador e coletor.
- Selecionar, aceitar, rejeitar, cancelar e concluir solicitacoes.
- Sincronizar dados de coletores e enderecos recebidos por eventos Kafka.
- Enriquecer enderecos usando servicos externos.
- Sugerir rotas para coletores com base em veiculos, capacidade e coletas em andamento.
- Salvar, listar, mover coletas entre veiculos e excluir sugestoes de rota.

O servico foi implementado com Quarkus, Java 21, MongoDB, Kafka e arquitetura orientada a portas e adaptadores.

## 2. Endpoints da API

A API REST usa o prefixo `/api`.

| Metodo | URL | Descricao |
| --- | --- | --- |
| `POST` | `/api/generators/requests` | Cria uma nova solicitacao de coleta. |
| `GET` | `/api/generators/requests/{requestId}/collectors` | Lista coletores proximos e elegiveis para uma solicitacao. |
| `POST` | `/api/generators/requests/{requestId}/cancel` | Cancela uma solicitacao pelo gerador. |
| `GET` | `/api/collectors/{collectorId}/address` | Retorna o endereco cadastrado de um coletor. |
| `POST` | `/api/collectors/requests/{requestId}/select` | Seleciona um coletor para uma solicitacao. |
| `POST` | `/api/collectors/requests/{requestId}/accept` | Aceita uma solicitacao pelo coletor e coloca a coleta em andamento. |
| `POST` | `/api/collectors/requests/{requestId}/reject` | Rejeita uma solicitacao pelo coletor. |
| `POST` | `/api/collectors/requests/{requestId}/cancel` | Cancela uma solicitacao pelo coletor. |
| `POST` | `/api/requests/{requestId}/confirm-generator` | Registra a confirmacao de conclusao pelo gerador. |
| `POST` | `/api/requests/{requestId}/confirm-collector` | Registra a confirmacao de conclusao pelo coletor. |
| `GET` | `/api/collections/search` | Pesquisa coletas por `status`, `collectorId` e `generatorId`, ordenando as mais recentes primeiro. |
| `GET` | `/api/collections/{id}` | Busca uma coleta pelo identificador. |
| `POST` | `/api/collectors/routes/suggest` | Gera sugestao de rota para coletas com status `IN_PROGRESS`. |
| `POST` | `/api/collectors/routes/save` | Salva uma sugestao de rota. |
| `GET` | `/api/collectors/routes/saved` | Lista todas as rotas salvas. |
| `POST` | `/api/collectors/routes/saved/{savedRouteId}/move-request` | Move uma coleta entre veiculos de uma rota salva e recalcula as paradas. |
| `DELETE` | `/api/collectors/routes/saved/{savedRouteId}` | Remove uma sugestao de rota salva. |

## 3. Exemplos de requisicao e resposta

### Criar solicitacao de coleta

**Requisicao**

```http
POST /api/generators/requests
Content-Type: application/json
```

```json
{
  "generatorId": "generator-001",
  "addressId": "address-001",
  "materialIds": ["paper", "plastic"],
  "weight": 12.5
}
```

**Resposta esperada**

```json
{
  "id": "collection-001",
  "generatorId": "generator-001",
  "collectorId": null,
  "addressId": "address-001",
  "materialIds": ["paper", "plastic"],
  "weight": 12.5,
  "status": "PENDING",
  "createdAt": "2026-05-23T10:00:00Z",
  "updatedAt": "2026-05-23T10:00:00Z"
}
```

### Pesquisar coletas

**Requisicao**

```http
GET /api/collections/search?status=IN_PROGRESS&collectorId=collector-001&generatorId=generator-001
```

**Resposta esperada**

```json
[
  {
    "id": "collection-002",
    "generatorId": "generator-001",
    "collectorId": "collector-001",
    "addressId": "address-002",
    "materialIds": ["metal"],
    "weight": 8.0,
    "status": "IN_PROGRESS",
    "createdAt": "2026-05-23T12:00:00Z",
    "updatedAt": "2026-05-23T12:15:00Z"
  }
]
```

### Sugerir rota para coletor

**Requisicao**

```http
POST /api/collectors/routes/suggest
Content-Type: application/json
```

```json
{
  "collectorId": "collector-001",
  "vehicles": [
    {
      "capacity": 100.0
    },
    {
      "capacity": 80.0
    }
  ],
  "start": {
    "type": "COORDINATES",
    "latitude": -23.5505,
    "longitude": -46.6333
  },
  "endAtStart": true,
  "candidateRequestIds": ["collection-002", "collection-003"],
  "filters": {
    "materialIds": ["paper", "plastic"],
    "maxDistanceKmFromStart": 50.0,
    "onlyInProgress": true
  },
  "options": {
    "timeLimitSeconds": 5,
    "allowDroppingStops": true,
    "dropPenalty": 100000
  }
}
```

**Resposta esperada**

```json
{
  "collectorId": "collector-001",
  "engine": "OR_TOOLS",
  "routes": [
    {
      "vehicleIndex": 0,
      "totalWeight": 20.5,
      "totalDistanceKm": 14.2,
      "stops": [
        {
          "sequence": 1,
          "collectionRequestId": "collection-002",
          "addressId": "address-002",
          "weight": 8.0,
          "distanceFromPreviousKm": 4.6
        },
        {
          "sequence": 2,
          "collectionRequestId": "collection-003",
          "addressId": "address-003",
          "weight": 12.5,
          "distanceFromPreviousKm": 9.6
        }
      ]
    }
  ],
  "unassigned": [],
  "metadata": {
    "timeLimitSeconds": 5,
    "endAtStart": true
  }
}
```

## 4. Dependencias externas

| Dependencia | Tipo | Uso |
| --- | --- | --- |
| MongoDB | Banco de dados | Persistencia de coletas, coletores, enderecos, cache de enderecos e rotas salvas. |
| Kafka / Aiven Kafka | Broker de mensagens | Consumo de eventos de sincronizacao e publicacao de eventos de coleta. |
| ViaCEP | API externa | Enriquecimento de enderecos a partir de CEP. |
| Nominatim OpenStreetMap | API externa | Geocodificacao e obtencao de coordenadas de enderecos. |
| Google OR-Tools | Biblioteca nativa | Otimizacao das rotas sugeridas para os veiculos do coletor. |
| Servico de cadastro/usuarios | Microsservico externo | Origem esperada dos eventos de sincronizacao de coletores e enderecos. |

Principais variaveis de ambiente:

```env
QUARKUS_MONGODB_CONNECTION_STRING=mongodb+srv://<usuario>:<senha>@<cluster>/<database>
QUARKUS_KAFKA_BOOTSTRAP_SERVERS=<host-kafka>:<porta>
KAFKA_SECURITY_PROTOCOL=SASL_SSL
KAFKA_SASL_MECHANISM=PLAIN
KAFKA_USERNAME=<usuario-kafka>
KAFKA_PASSWORD=<senha-kafka>
KAFKA_SSL_TRUSTSTORE_TYPE=PEM
KAFKA_SSL_TRUSTSTORE_LOCATION=ca.pem
PORT=8080
```

## 5. Responsavel pelo servico

**Responsavel:** Lidia Galdino / Equipe Cyco

O responsavel pelo servico deve manter os contratos da API, a configuracao de infraestrutura, os topicos Kafka, as regras de negocio de coleta e a documentacao operacional atualizados.

## 6. Procedimentos basicos de operacao

### Executar localmente

Pre-requisitos:

- Java 21.
- Maven ou Maven Wrapper.
- MongoDB acessivel.
- Kafka acessivel.
- Arquivo `ca.pem`, quando o broker Kafka exigir conexao `SASL_SSL` com certificado.

Passos:

```bash
./mvnw quarkus:dev
```

Ou, caso esteja usando Maven instalado localmente:

```bash
mvn quarkus:dev
```

O servico sobe por padrao na porta `8080`, respeitando a variavel `PORT` quando definida.

### Executar com Docker

```bash
docker build -t cyco-collections .
docker run --env-file .env -p 8080:8080 cyco-collections
```

### Gerar artefato executavel

```bash
./mvnw clean package -DskipTests -Dquarkus.package.type=uber-jar
java -jar target/*-runner.jar
```

### Verificar logs

Ambiente local:

```bash
./mvnw quarkus:dev
```

Os logs aparecem no terminal onde o servico foi iniciado.

Google Cloud Run:

```bash
gcloud run services logs read cyco-collections --region southamerica-east1
```

### Endpoint de health check

O codigo atual nao possui a extensao `quarkus-smallrye-health` configurada no `pom.xml`. Por isso, nao ha um endpoint de health check ativo no servico neste momento.

Recomendacao operacional:

- Adicionar a extensao `quarkus-smallrye-health`.
- Usar o endpoint padrao `/q/health` apos a configuracao.
- Enquanto isso, validar disponibilidade por logs, status do container e chamadas de API conhecidas.

### Reiniciar o servico

Ambiente local:

1. Encerrar o processo com `Ctrl+C`.
2. Iniciar novamente com `./mvnw quarkus:dev`.

Google Cloud Run:

```bash
gcloud run deploy cyco-collections \
  --image <imagem-do-container> \
  --region southamerica-east1 \
  --platform managed
```

## 7. Regras de negocio

- Uma solicitacao de coleta deve possuir `generatorId`, `addressId`, pelo menos um material e peso maior que zero.
- Uma nova solicitacao inicia com status `PENDING`.
- Um coletor pode ser selecionado para uma solicitacao pendente quando for elegivel para os materiais e localizacao da coleta.
- Quando o coletor aceita a solicitacao, a coleta passa para `IN_PROGRESS`.
- O gerador e o coletor podem cancelar a coleta enquanto ela ainda nao estiver finalizada.
- O cancelamento deve validar se o usuario informado e o gerador ou coletor associado a solicitacao.
- A conclusao da coleta depende da confirmacao do gerador e do coletor.
- A coleta so deve ser considerada concluida quando as confirmacoes obrigatorias forem registradas.
- A pesquisa de coletas deve permitir filtro por `status`, `collectorId` e `generatorId`.
- A pesquisa de coletas deve retornar os registros em ordem decrescente de data de criacao, ou seja, os mais recentes primeiro.
- A sugestao de rota so pode considerar coletas com status `IN_PROGRESS`.
- Coletas que nao estao em andamento devem ser retornadas como nao alocadas com motivo `NOT_IN_PROGRESS`.
- A requisicao de sugestao de rota deve informar explicitamente cada veiculo e sua capacidade, pois veiculos diferentes podem suportar pesos diferentes.
- A capacidade de cada veiculo deve ser maior que zero.
- O otimizador pode descartar paradas quando `allowDroppingStops` estiver habilitado, aplicando o custo configurado em `dropPenalty`.
- Ao salvar uma rota, o servico deve bloquear sugestoes duplicadas.
- Uma rota salva deve ser fechada quando todas as coletas associadas estiverem concluidas.
- Ao mover uma coleta entre veiculos de uma rota salva, o sistema deve recalcular automaticamente a melhor posicao da parada no veiculo de destino.

## 8. Eventos publicados ou consumidos

### Eventos publicados

| Topico | Evento | Descricao |
| --- | --- | --- |
| `collection-events` | `COLLECTOR_SELECTED` | Publicado quando um coletor e selecionado para uma solicitacao. |
| `collection-events` | `COLLECTION_ACCEPTED` | Publicado quando o coletor aceita uma solicitacao. |
| `collection-events` | `COLLECTION_REJECTED` | Publicado quando o coletor rejeita uma solicitacao. |
| `collection-events` | `COLLECTION_COMPLETED` | Publicado quando a coleta e concluida. |

### Eventos consumidos

| Topico | Evento/Dado | Descricao |
| --- | --- | --- |
| `addresses-sync` | `SyncAddressEvent` | Sincroniza dados de endereco e executa enriquecimento quando necessario. |
| `collector-sync` | `SyncCollectorEvent` | Cria ou atualiza o snapshot de um coletor no servico de coletas. |
| `collector-update` | `SyncCollectorEvent` | Atualiza dados de um coletor existente usando o mesmo formato do evento de criacao. |

## 9. Metricas monitoradas

Metricas recomendadas para operacao do microsservico:

- Quantidade de requisicoes por endpoint.
- Taxa de respostas `2xx`, `4xx` e `5xx`.
- Latencia media, p95 e p99 dos endpoints REST.
- Tempo de resposta do MongoDB.
- Erros de leitura e escrita no MongoDB.
- Lag dos consumidores Kafka.
- Falhas de publicacao no topico `collection-events`.
- Quantidade de eventos consumidos por topico.
- Taxa de sucesso e falha no enriquecimento de enderecos.
- Tempo gasto para gerar sugestoes de rota.
- Quantidade de coletas nao alocadas em sugestoes de rota.
- Uso de CPU e memoria do container.
- Numero de reinicios do container em ambiente de deploy.

## 10. ADR relacionado

### ADR-001: Arquitetura hexagonal com Quarkus

O servico utiliza uma organizacao baseada em camadas de dominio, aplicacao, infraestrutura e apresentacao. Essa decisao isola regras de negocio de detalhes externos como MongoDB, Kafka e APIs REST.

### ADR-002: Sincronizacao por eventos Kafka

Dados de coletores e enderecos sao mantidos localmente por meio de eventos Kafka. Essa abordagem reduz acoplamento direto com outros microsservicos e permite que o servico de coletas opere com snapshots locais.

### ADR-003: Otimizacao de rotas com OR-Tools e fallback

A sugestao de rotas usa Google OR-Tools quando a biblioteca nativa esta disponivel. Caso ocorra falha de carregamento nativo no ambiente, o servico usa uma estrategia interna de fallback para evitar indisponibilidade do endpoint.

