# Deploy do Gestor Financeiro (MEI) em servidor caseiro com Docker

Documento do processo realizado em 17/10/2026. Cobre o servidor, as imagens, a operação do dia a dia e a geração do APK Android.

## 1. Visão geral

```
Celular / navegador ──HTTP:80──► [web: nginx] ──/api/──► [api: Quarkus :8080] ──► [db: MariaDB :3306]
                                  serve o Angular           logs em arquivo         dados em volume
```

| Item | Valor |
|---|---|
| Servidor | Armbian 26.11 (Debian 12), aarch64, ~800 MB RAM, 14 GB de disco, IP `192.168.15.29` |
| Containers | `gestor-web-1` (nginx), `gestor-api-1` (Quarkus 3.35, Java 25), `gestor-db-1` (MariaDB 11) |
| Pasta no servidor | `/opt/gestor` |
| Acesso | `http://192.168.15.29` (somente rede local) |
| Build | Feito no PC com Docker Desktop (imagens `linux/arm64`) e enviado por SSH |

O build é feito no PC porque 800 MB de RAM não comportam Maven/Java 25 nem o Angular.

### Arquivos do repositório

| Arquivo | Função |
|---|---|
| `backend/gerenciador-api/Dockerfile` | Build multi-stage do backend (Maven → JRE 25) |
| `backend/gerenciador-api/Dockerfile.dockerignore` | Exclusões do contexto de build |
| `Gestor-Financeiro-FRONTEND/Dockerfile` | Build Angular (Node 16) → nginx. Argumento `CONFIGURATION` (padrão `production`) |
| `Gestor-Financeiro-FRONTEND/nginx.conf` | SPA fallback e proxy `/api/` → `api:8080` |
| `Gestor-Financeiro-FRONTEND/src/environments/environment.prod.ts` | `apiUrl: '/api'` (mesma origem, sem CORS) |
| `Gestor-Financeiro-FRONTEND/src/environments/environment.mobile.ts` | `apiUrl: 'http://192.168.15.29/api'` (usado pelo APK) |
| `deploy/docker-compose.yml` | Define os 3 serviços |
| `deploy/.env.example` | Modelo das variáveis secretas |
| `deploy/secrets/` | **Não versionar.** `.env` real, chaves JWT e logs de build |

## 2. Preparação do servidor (feita uma vez)

Como root via SSH:

```bash
# Swap de 1 GB (o servidor não tinha swap)
fallocate -l 1G /swapfile && chmod 600 /swapfile && mkswap /swapfile && swapon /swapfile
echo '/swapfile none swap sw 0 0' >> /etc/fstab

# Pastas da aplicação (logs pertencem ao UID 185, usuário do container Java)
mkdir -p /opt/gestor/{keys,logs,db}
chown 185:185 /opt/gestor/logs

# Docker + Compose
curl -fsSL https://get.docker.com | sh
systemctl enable --now docker
```

## 3. Segredos

Gerados no PC e **nunca commitados**:

1. **Chaves JWT novas** (as do repositório foram descartadas, pois a privada estava commitada):
   `java backend/gerenciador-api/GenerateKeys.java` dentro de `deploy/secrets/` → `privateKey.pem` e `publicKey.pem`.
   No servidor ficam em `/opt/gestor/keys` (montadas somente leitura no container).
2. **`.env`** com `DB_PASSWORD`, `DB_ROOT_PASSWORD`, `APP_CRYPTO_SECRET` (aleatórios, 24+ bytes) e `CORS_ORIGIN`.
   No servidor: `/opt/gestor/.env` com `chmod 600`.

> **Guarde uma cópia do `.env`.** Sem o `APP_CRYPTO_SECRET` os CPF/CNPJ já gravados ficam ilegíveis.

`CORS_ORIGIN` atual: `http://192.168.15.29,http://localhost` (o segundo é a origem do app Android).

## 4. Build das imagens (no PC)

Pré-requisito: Docker Desktop em execução. Na primeira vez, habilitar emulação arm64:

```powershell
docker run --privileged --rm tonistiigi/binfmt --install arm64
```

```powershell
cd C:\TCC
docker buildx build --platform linux/arm64 -t gestor-api:latest --load backend/gerenciador-api
docker buildx build --platform linux/arm64 -t gestor-web:latest --load Gestor-Financeiro-FRONTEND
```

Tempos observados: backend ~3 min (o Maven roda na arquitetura do PC; só a imagem final é arm64); frontend ~15 min (o Angular roda emulado, e a etapa "Generating ES5 bundles" é lenta e parece travada, mas não está).

## 5. Envio e subida

```powershell
docker save gestor-api:latest gestor-web:latest -o imgs.tar
scp imgs.tar deploy\docker-compose.yml deploy\secrets\.env root@192.168.15.29:/opt/gestor/
scp deploy\secrets\privateKey.pem deploy\secrets\publicKey.pem root@192.168.15.29:/opt/gestor/keys/
ssh root@192.168.15.29
```

No servidor:

```bash
cd /opt/gestor
chmod 600 .env keys/*; chmod 644 keys/publicKey.pem; chown -R 185 keys
docker load -i imgs.tar && rm imgs.tar
docker compose up -d
docker compose ps
```

Detalhes da configuração (em `docker-compose.yml`):
- MariaDB: `innodb-buffer-pool-size=64M`, `max-connections=30`, limite 220 MB.
- API: `-Xmx256m`, limite 400 MB, OpenTelemetry desligado (`QUARKUS_OTEL_SDK_DISABLED`), chaves JWT por `file:/keys/...`.
- Web: limite 50 MB, porta 80.
- As tabelas são criadas pelo Hibernate (`generation=update`) na primeira subida.

## 6. Logs

| O quê | Onde |
|---|---|
| Log do backend (arquivo, rotação 10 MB × 5) | `/opt/gestor/logs/app.log` |
| Log do container (rotação 10 MB × 3) | `docker compose logs -f api` |

```bash
tail -f /opt/gestor/logs/app.log
```

## 7. Reinício automático

Já configurado, nada a fazer: o serviço `docker` está habilitado no boot, os 3 containers têm `restart: unless-stopped` e o swap está no `/etc/fstab`. Depois de reiniciar o servidor, **aguarde 1 a 2 minutos**: a API leva ~70 s para iniciar e nesse intervalo o nginx responde 502.

## 8. Operação

```bash
cd /opt/gestor
docker compose ps                 # estado
docker compose restart api        # reiniciar um serviço
docker compose down               # parar tudo (os dados em ./db permanecem)
docker compose up -d              # subir
free -h                           # memória/swap
```

**Atualizar a aplicação:** refazer o build (seção 4), `docker save`, `scp`, `docker load` e `docker compose up -d`.

**Backup do banco** (recomendado em cron diário):

```bash
cd /opt/gestor
docker compose exec -T db sh -c 'mariadb-dump -ugestor -p"$MARIADB_PASSWORD" gestor_mei' | gzip > /root/backup-$(date +%F).sql.gz
```

Copie também `.env` e `keys/`.

## 9. APK Android (Poco X7 Pro)

O app usa Capacitor. O APK carrega o site empacotado e chama a API em `http://192.168.15.29/api`, então **só funciona com o celular na mesma rede Wi-Fi do servidor** (ou via VPN).

Ajustes feitos no código:
- `environment.mobile.ts` e a configuração `mobile` em `angular.json`.
- `capacitor.config.ts`: `server.androidScheme = "http"` e `cleartext = true` (necessário para falar HTTP sem TLS; com o esquema `https` padrão o Android bloquearia as chamadas como conteúdo misto).
- CORS da API aceitando `http://localhost`.

Pré-requisitos no PC: Android SDK (Android Studio, plataforma 34) e **JDK 17**. O Gradle 7.4.2 do projeto não funciona com o Java 25.

```powershell
cd C:\TCC\Gestor-Financeiro-FRONTEND

# 1) Bundle Angular em modo mobile (Node 16 via Docker) e cópia para www/
docker build --target build --build-arg CONFIGURATION=mobile -t gestor-web-mobile .
docker create --name wwwx gestor-web-mobile
Remove-Item -Recurse -Force www; docker cp wwwx:/app/www www; docker rm wwwx

# 2) Sincronizar com o projeto Android
npx cap sync android

# 3) Compilar (JAVA_HOME apontando para um JDK 17)
$env:JAVA_HOME = "<pasta do JDK 17>"
$env:ANDROID_HOME = "$env:LOCALAPPDATA\Android\Sdk"
cd android; .\gradlew.bat assembleDebug
```

O APK sai em `android/app/build/outputs/apk/debug/app-debug.apk`.

**Instalar no celular:** copie o arquivo (cabo USB, Drive, WhatsApp), abra-o e permita "instalar apps de fontes desconhecidas" quando o Android pedir. Pelo cabo: `adb install -r app-debug.apk` (depuração USB ativada).

Observações:
- É um APK **debug** (assinado com a chave de depuração). Para publicar na Play Store é preciso gerar um APK/AAB release com keystore própria.
- App: nome "Finanças MEI", `applicationId` `com.diegomarques.gestorfinanceiro`, `targetSdk` 34 (com `targetSdk` 32 o Play Protect exibia "app criado para uma versão mais antiga do Android"). O ícone está em `android/app/src/main/res/mipmap-*`.
- Se o IP do servidor mudar, edite `environment.mobile.ts` e `CORS_ORIGIN` e refaça os passos. Recomenda-se fixar o IP no roteador (reserva de DHCP).

## 10. Problemas encontrados e soluções

| Sintoma | Causa | Solução |
|---|---|---|
| `mvnw` falha com código 127 no Docker | Quebras de linha CRLF do Windows | Deixou de usar o wrapper |
| `Failed to validate Maven distribution SHA-256` | Download do Maven pelo wrapper rejeitado | Usar a imagem `maven:3.9-eclipse-temurin-25` |
| Build do Angular parece travado | ES5 (differential loading) emulado em arm64 | Esperar (~10 min); para o APK, o build roda sem emulação |
| 502 logo após subir | A API demora ~70 s para iniciar | Aguardar |
| Aviso `Unknown column 'RESERVED'` no log | `db-version=5.5.5` com MariaDB | Inofensivo; tabelas são criadas normalmente |
| Gradle falha com Java 25 | Gradle 7.4.2 só suporta até JDK 17 | Usar JDK 17 em `JAVA_HOME` |

## 11. Pendências de segurança

- Trocar a senha do root (foi exposta durante o processo), usar chave SSH e definir `PermitRootLogin prohibit-password`.
- O tráfego é HTTP puro na rede local; para acesso pela internet seria preciso HTTPS (proxy reverso com certificado) e rever o CORS.
- A chave JWT antiga e `private-key.pem` ainda estão na árvore do backend; remova-as do Git.
