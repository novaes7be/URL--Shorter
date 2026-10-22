# ShortURL: encurtador de URLs com Redis, Postgres e Spring Boot

API REST que encurta URLs, redireciona, conta cliques e monta um ranking dos links mais acessados.
O projeto foi feito para **praticar Redis na prática** e para estudar padrões usados em sistemas reais: cache, contadores, expiração e testes de integração com containers.

---

## Sumário

1. [Funcionalidades](#funcionalidades)
2. [Tecnologias](#tecnologias)
3. [Arquitetura e fluxo](#arquitetura-e-fluxo)
4. [Endpoints](#endpoints)
5. [Como rodar](#como-rodar)
6. [Como rodar os testes](#como-rodar-os-testes)
7. [Lições de teoria](#lições-de-teoria)
    - [Redis](#redis)
    - [Postgres e JPA](#postgres-e-jpa)
    - [Containers e Docker](#containers-e-docker)
    - [Testes](#testes)
    - [Annotations](#annotations)
8. [Decisões e limitações](#decisões-e-limitações)
9. [Próximos passos](#próximos-passos)

---

## Funcionalidades

- Encurtar uma URL e receber um código de 7 caracteres (letras e números).
- Redirecionar `GET /{code}` para a URL original (HTTP 302).
- Contar cliques de cada link.
- Consultar estatísticas de um link.
- Ver o ranking dos links mais acessados.
- Cache com expiração (TTL) configurável.
- Postgres como fonte da verdade e Redis como cache (padrão **Cache-Aside**).
- Testes de integração com **Testcontainers** (Postgres e Redis reais, sem mocks).

## Tecnologias

| Tecnologia | Uso |
|---|---|
| Java 17 | Linguagem |
| Spring Boot 4 | Framework da API |
| Spring Data Redis | Acesso ao Redis (`StringRedisTemplate`) |
| Spring Data JPA + Hibernate | Persistência no Postgres |
| Redis 7 | Cache, contadores e ranking |
| PostgreSQL 16 | Banco de dados principal |
| Bean Validation | Validação do corpo das requisições |
| Lombok | Menos código repetitivo |
| JUnit 5 + MockMvc | Testes |
| Testcontainers | Containers descartáveis nos testes |
| Docker | Subir Redis e Postgres localmente |

## Arquitetura e fluxo

```
Cliente
   |
   v
URLController  ->  UrlService  ->  Redis (cache, cliques, ranking)
                        |
                        v
                  ShortUrlRepository  ->  Postgres (fonte da verdade)
```

### Encurtar (`POST /shorten`)

1. O service gera um código aleatório de 7 caracteres (Base62, com `SecureRandom`).
2. Se o código já existe no banco, gera outro (tratamento de colisão).
3. Salva a URL no **Postgres**.
4. Salva também no **Redis** com TTL (`url:{code}`).

### Redirecionar (`GET /{code}`): Cache-Aside

1. Procura `url:{code}` no Redis.
2. **Cache hit**: devolve a URL imediatamente.
3. **Cache miss**: busca no Postgres, grava no Redis com TTL e devolve.
4. Se não existe em lugar nenhum: **404**.
5. Quando o link existe, registra o clique (`INCR` e ranking).

### Chaves usadas no Redis

| Chave | Tipo | Função |
|---|---|---|
| `url:{code}` | String (com TTL) | Cache da URL original |
| `clicks:{code}` | String numérica (com TTL) | Contador de cliques |
| `ranking` | Sorted Set | Ranking dos links mais acessados |

## Endpoints

| Método | Rota | Descrição | Resposta |
|---|---|---|---|
| POST | `/shorten` | Encurta uma URL | `201` + código |
| GET | `/{code}` | Redireciona para a URL original | `302` ou `404` |
| GET | `/stats/{code}` | Total de cliques do link | `200` + número |
| GET | `/ranking?limit=10` | Links mais acessados | `200` + lista |

**Exemplo: encurtar**

```http
POST /shorten
Content-Type: application/json

{ "url": "https://google.com" }
```

Resposta: `201 Created` com o código no corpo, por exemplo `AWCAcaw`.

**Exemplo: ranking**

```json
[
  { "code": "S8eQVqF", "clicks": 3 },
  { "code": "t8SvKV7", "clicks": 2 }
]
```

O parâmetro `limit` precisa ser maior ou igual a 1 (valores inválidos retornam `400`).

## Como rodar

### Pré-requisitos

- Java 17
- Maven
- Docker

### 1. Subir o Redis

```bash
docker run -d --name redis -p 6379:6379 redis:7
```

### 2. Subir o Postgres

```bash
docker run -d --name postgres-shorturl -p 5433:5432 \
  -e POSTGRES_USER=<usuario> \
  -e POSTGRES_PASSWORD=<senha> \
  -e POSTGRES_DB=shorturl \
  postgres:16
```

A porta do host é `5433` para não conflitar com um Postgres instalado localmente (que normalmente usa a `5432`).

### 3. Configurar as variáveis de ambiente

As credenciais **não ficam no código**. A aplicação lê:

| Variável | Descrição | Padrão |
|---|---|---|
| `DB_URL` | URL JDBC | `jdbc:postgresql://localhost:5433/shorturl` |
| `DB_USER` | Usuário do banco | `postgres` |
| `DB_PASSWORD` | Senha do banco | sem padrão (obrigatória) |

Defina `DB_USER` e `DB_PASSWORD` no terminal ou na Run Configuration da sua IDE.

O TTL dos links é configurado em `application.properties`:

```properties
shorturl.ttl-seconds=604800
```

(`604800` segundos = 7 dias.)

### 4. Subir a aplicação

```bash
mvn spring-boot:run
```

## Como rodar os testes

Os testes usam **Testcontainers**: ele sobe um Postgres e um Redis próprios, descartáveis, apenas para os testes. **O Docker precisa estar aberto.**

```bash
mvn test
```

Cenários cobertos em `ShortUrlIntegrationTest`:

1. Criar um link e validar o formato do código.
2. Redirecionar para a URL original (302 + header `Location`).
3. Código inexistente retorna 404.
4. Contagem de cliques.
5. **Cache-Aside**: apaga a chave do Redis e confirma que o link continua funcionando (veio do Postgres) e que o cache foi repovoado.

---

## Lições de teoria

### Redis

**O que é:** um banco de dados **em memória**, do tipo chave-valor. Por viver na RAM, responde em microssegundos. É muito usado para cache, contadores, filas, sessões e rankings.

**Estruturas de dados usadas neste projeto**

| Estrutura | Comandos | Uso no projeto |
|---|---|---|
| String | `SET`, `GET` | Guardar a URL original por código |
| String numérica | `INCR` | Contador de cliques (operação atômica) |
| Sorted Set | `ZINCRBY`, `ZREVRANGE` | Ranking ordenado por número de cliques |

**TTL (Time To Live):** tempo de vida de uma chave. Passado esse tempo, o Redis remove a chave sozinho. Comandos úteis no `redis-cli`:

- `TTL chave`: segundos restantes. `-1` = sem expiração, `-2` = a chave não existe.
- `GET chave`: lê o valor.
- `KEYS *`: lista as chaves (use só em estudo).

**`INCR` é atômico:** mesmo com várias requisições simultâneas, o Redis não perde contagens. Fazer "ler, somar e gravar" na aplicação poderia perder cliques.

**Sorted Set (ZSET):** cada membro tem um **score**. O Redis mantém tudo ordenado por score. Aqui, o membro é o código do link e o score é o número de cliques.

**Cache-Aside (Lazy Loading)**

1. A aplicação consulta o cache.
2. Se achou (**hit**), devolve.
3. Se não achou (**miss**), busca na fonte da verdade (Postgres), grava no cache e devolve.

Vantagens: simples, o cache só guarda o que é realmente acessado, e se o Redis perder os dados nada se perde (o Postgres tem tudo).

### Postgres e JPA

**Postgres:** banco relacional que grava em disco. Aqui é a **fonte da verdade**: se o Redis for limpo, os links continuam existindo.

**Por que dois bancos?**

- **Postgres**: durável, consistente, bom para guardar dados que não podem ser perdidos.
- **Redis**: muito rápido, ideal para leituras frequentes e contadores.

**JPA e Hibernate:** JPA é a especificação para mapear classes Java em tabelas. Hibernate é a implementação. A classe `ShortUrl` vira a tabela `short_url`.

**Spring Data JPA:** basta declarar uma interface que estende `JpaRepository<ShortUrl, Long>`. Métodos como `save`, `findById` e `delete` já vêm prontos. Métodos como `findByCode` e `existsByCode` são **gerados a partir do nome**.

**`ddl-auto=update`:** o Hibernate cria e ajusta as tabelas automaticamente. Ótimo para estudo. Em produção, o ideal é usar migrações (Flyway ou Liquibase).

### Containers e Docker

**Container:** um processo isolado que carrega a aplicação com tudo o que ela precisa. É mais leve que uma máquina virtual, porque compartilha o kernel do sistema.

**Imagem x container**

- **Imagem**: o "molde", somente leitura (ex.: `redis:7`).
- **Container**: uma instância em execução criada a partir da imagem.

**Conceitos usados aqui**

| Conceito | O que faz |
|---|---|
| `-p 5433:5432` | Mapeia a porta do seu computador (5433) para a porta do container (5432) |
| `-e VAR=valor` | Define variáveis de ambiente no container |
| tag (`:7`, `:16`) | Fixa a versão da imagem, evitando surpresas com `latest` |
| `-d` | Roda em segundo plano |

**Dica:** apagar um container apaga os dados dele (a menos que use volumes). As imagens permanecem.

**Segredos:** senhas ficam em variáveis de ambiente e em arquivos `.env` fora do git. O repositório traz apenas um `.env.example` com valores fictícios.

### Testes

**Tipos de teste**

| Tipo | O que testa | Velocidade |
|---|---|---|
| Unitário | Uma classe isolada, com dependências simuladas | Muito rápido |
| Integração | Várias partes juntas (controller, service, bancos) | Mais lento |
| Ponta a ponta | O sistema completo, como um usuário | Mais lento ainda |

Este projeto usa **testes de integração**, porque o valor está em verificar que Spring, Redis e Postgres funcionam juntos.

**Por que Testcontainers em vez de mocks?**

- Mock simula o comportamento e pode divergir do real.
- Com containers reais, o teste roda contra o **mesmo tipo de banco** que existe em produção.
- Cada execução começa limpa e não depende do que você tem instalado ou rodando na máquina.

**MockMvc:** simula requisições HTTP **sem subir um servidor de verdade**. Você faz `perform(get(...))` e verifica status, headers e corpo com `andExpect`.

**Estrutura de um bom teste:** preparar (arrange), executar (act) e verificar (assert).

**O teste de Cache-Aside:** apaga a chave do Redis na mão, faz a requisição e confirma que a resposta veio do Postgres e que a chave voltou ao Redis. É o teste que prova o padrão funcionando.

**Dicas aprendidas**

- Containers `static` sobem uma vez por classe, o que é bem mais rápido.
- Como os códigos são aleatórios e únicos, os testes não interferem entre si, mesmo compartilhando os mesmos containers.
- Cuidado com os imports estáticos: `MockMvcResultMatchers` (e não os matchers de `test.web.client`).

### Annotations

**Spring e Spring MVC**

| Annotation | Função |
|---|---|
| `@SpringBootApplication` | Marca a classe principal e liga a autoconfiguração |
| `@RestController` | Controller que devolve dados (JSON/texto) em vez de páginas |
| `@Service` | Classe com regra de negócio, registrada como bean |
| `@Component` | Bean genérico gerenciado pelo Spring |
| `@GetMapping` / `@PostMapping` | Ligam um método a uma rota HTTP |
| `@PathVariable` | Pega um valor da URL (`/{code}`) |
| `@RequestParam` | Pega um parâmetro de query (`?limit=10`) |
| `@RequestBody` | Converte o JSON do corpo da requisição em objeto |
| `@ResponseStatus` | Define o status HTTP da resposta |
| `@Value("${...}")` | Injeta uma propriedade do `application.properties` |

**Validação**

| Annotation | Função |
|---|---|
| `@Valid` | Dispara a validação do objeto recebido |
| `@NotBlank` | O texto não pode ser nulo nem vazio |
| `@Min(1)` | Valor mínimo permitido |
| `@Validated` | Habilita validação de parâmetros simples (como `@RequestParam`) |

**JPA**

| Annotation | Função |
|---|---|
| `@Entity` | Marca a classe como tabela |
| `@Id` | Chave primária |
| `@GeneratedValue` | Gera o id automaticamente |
| `@Column` | Configura a coluna (`unique`, `nullable`) |
| `@PrePersist` | Método executado automaticamente antes do `INSERT` (usado para preencher `createdAt`) |

**Lombok**

| Annotation | Função |
|---|---|
| `@RequiredArgsConstructor` | Cria o construtor com os campos `final` (injeção por construtor) |
| `@Getter` / `@Setter` | Geram getters e setters |
| `@NoArgsConstructor` | Construtor vazio (o JPA exige) |

**Testes**

| Annotation | Função |
|---|---|
| `@Test` | Marca um método como teste |
| `@SpringBootTest` | Sobe o contexto completo da aplicação |
| `@AutoConfigureMockMvc` | Disponibiliza o `MockMvc` |
| `@Testcontainers` | Ativa o suporte do Testcontainers no JUnit |
| `@Container` | Gerencia o ciclo de vida do container (sobe antes, derruba depois) |
| `@ServiceConnection` | Conecta a aplicação ao container automaticamente, sem configurar host e porta |
| `@Autowired` | Injeta um bean no teste |

---

## Decisões e limitações

- **Injeção por construtor** (`@RequiredArgsConstructor`) em vez de `@Autowired` nos campos, o que facilita testes e deixa as dependências explícitas.
- **Credenciais fora do código**, lidas de variáveis de ambiente.
- **Cache manual** (Cache-Aside escrito à mão) em vez de `@Cacheable`, para entender o que acontece a cada passo.
- **Limitação:** o TTL vale para o **cache**. No Postgres o link não expira. Uma melhoria futura é comparar `createdAt` com o TTL para expirar de verdade.
- **Limitação:** no ranking, códigos expirados são filtrados depois do corte do `limit`, então a lista pode vir com menos itens que o pedido.

## Próximos passos

- Expiração real dos links no banco.
- Limpar do ranking os códigos expirados.
- Rate limiting por IP usando Redis.
- Pub/Sub para notificar eventos de clique.
- Documentação da API com OpenAPI/Swagger.
- Migrações com Flyway.