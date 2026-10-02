# BattleGrid — Operation Relay

A turn-based browser strategy game with a Java 17 / Spring Boot backend.
This is the first playable milestone toward the multiplayer AWS project.

## Start here on Windows

1. Download and extract the ZIP. Open the **battlegrid** folder in VS Code.
   Choose the folder that directly contains `pom.xml`.
2. Open **Terminal → New Terminal**. Confirm Java 17+ and Maven are available:

   ```powershell
   java -version
   mvn -version
   ```

   If `mvn` is not recognized, install Apache Maven and add its `bin` directory
   to your Windows PATH, then reopen VS Code. If Java is missing, install a JDK
   (not just a JRE). If you used Maven for the calculator, reuse that installation.
3. Start the application:

   ```powershell
   mvn spring-boot:run
   ```

   The first run downloads dependencies and may take a few minutes.
4. Open **http://localhost:8080** in your browser. Keep the terminal running.
   Press **Ctrl+C** in the terminal to stop the server.

If port 8080 is already occupied (for example by the calculator):

```powershell
mvn spring-boot:run '-Dspring-boot.run.arguments=--server.port=8081'
```

Then open http://localhost:8081. Do not open `index.html` directly or with
VS Code Live Server: the game needs its Java API and session cookie.

## Your first battle

- Blue is you; red is the computer. Destroy the red base to win.
- Select the blue scout at **B7**, choose **Move**, then click **B6**.
  Your energy drops from 60 to 50.
- Select the blue tank at **C8**, click **Shield**. Energy drops to 35.
- Click **End turn**. The computer moves; your next turn starts with 65 energy.
- Moves and attacks are highlighted. Each unit gets one action per turn.
- Recruit scouts/tanks on empty highlighted tiles next to your base.
- Occupy diamond relays for extra energy. See the Field manual below the board
  for unit stats and complete rules.

## Understand the files

| File | Purpose |
| --- | --- |
| `pom.xml` | Java version, Spring dependencies, Maven build |
| `BattleGridApplication.java` | Starts the web server |
| `GameEngine.java` | Movement, attacks, energy, recruitment, turns, computer decisions |
| `GameController.java` | Browser-facing endpoints and per-session match storage |
| `index.html` | Page and board structure |
| `style.css` | Responsive visual design |
| `game.js` | Displays server state and sends commands |
| `application.properties` | Port, session settings, health endpoint |
| `EngineChecks.java` / `GameEngineTest.java` | Rules checks and complete simulated matches |
| `GameApiTest.java` | Spring API, session isolation, health and page tests |
| `.github/workflows/ci.yml` | Build/tests and deployment ZIP on GitHub-hosted runner |
| `Procfile` | Java startup command for a later Elastic Beanstalk deployment |

Java sources are in `src/main/java/com/example/battlegrid`.
Browser files are in `src/main/resources/static`.

## Requests

| Method and path | Behavior |
| --- | --- |
| `GET /api/game` | Get or initialize this browser session's match |
| `POST /api/game/new` | Reset this session's match |
| `POST /api/game/action` | Validate and apply a command |
| `GET /actuator/health` | Health status |

Example action JSON:

```json
{"type":"MOVE","unitId":"u2","row":5,"col":1,"recruitKind":null}
```

Rows and columns are zero-based. The server returns `legalActions`; the browser
sends one of those commands. End-turn uses `{"type":"END_TURN"}`.
The server checks every command; clients cannot set health, damage, or energy.
A rejected command returns HTTP 400 and leaves the match unchanged.

## Build and test

```powershell
mvn verify
java -jar target/battlegrid.jar
```

Maven creates `target/battlegrid.jar`. The project ZIP is source code, not a
precompiled JAR. A JDK-only rules check is available while learning:

```powershell
New-Item -ItemType Directory -Force target/rules-checks | Out-Null
javac -d target/rules-checks src/main/java/com/example/battlegrid/GameEngine.java src/test/java/com/example/battlegrid/EngineChecks.java
java -cp target/rules-checks com.example.battlegrid.EngineChecks
```

## GitHub milestone

Create an empty GitHub repository named `battlegrid` (skip initializing a README).
Run these commands from this project folder, replacing the URL with your repository:

```powershell
git init -b main
git add .
git commit -m "Build BattleGrid single-player tactical game"
git remote add origin https://github.com/YOUR_USERNAME/battlegrid.git
git push -u origin main
```

Under **Actions**, the build runs tests and creates an artifact called
`battlegrid-deploy`. Its deployment ZIP contains `battlegrid.jar` and `Procfile`
at the root. The workflow builds a bundle; it does **not** deploy to AWS yet.

## What is complete and what comes next

Complete: responsive board, scouts/tanks/bases, computer turns, movement path
validation, attacks, shields, recruitment, energy relays, battle log, victory/
defeat/draw rules, tests, Maven project and GitHub build workflow.

Next: run locally and confirm the board; push to GitHub; configure AWS deployment.
The first cloud milestone can run a single Java instance on Elastic Beanstalk.
Later we can add PostgreSQL profiles/match history, accounts, rooms, WebSockets,
Docker, and an ECS deployment pipeline with GitHub OIDC.

Current matches live in server memory and expire after 60 minutes of inactivity.
A server restart/deployment clears them. Tabs in the same browser share one
session; separate browsers have separate single-player matches. This milestone
has no multiplayer, login, permanent saves, leaderboard, or AWS resources.
Keep one application instance while sessions are in memory; horizontal scaling
requires shared match/session storage. Public cloud deployment will also need
HTTPS and abuse controls. We will configure these at the deployment milestone.

## Validation performed during creation

- Compiled the pure Java engine and standalone rules checks using Java 17.
- Ran 60 deterministic complete simulated matches, with movement, attack,
  shield and recruitment coverage and state invariants.
- Checked shield damage, victory, and the round limit with targeted scenarios.
- Checked JavaScript syntax with Node.
- Full Spring Boot/Maven integration tests were not run in the creation workspace:
  Maven is absent and Maven Central downloads are blocked. Run `mvn verify`
  locally or through the included GitHub workflow before deploying.

## Official references

- [Spring Boot 3.5 system requirements](https://docs.spring.io/spring-boot/3.5/system-requirements.html)
- [Running Spring Boot applications](https://docs.spring.io/spring-boot/3.5/reference/using/running-your-application.html)
- [AWS Java deployment quickstart](https://docs.aws.amazon.com/elasticbeanstalk/latest/dg/java-quickstart.html)
- [Apache Maven installation](https://maven.apache.org/install.html)
