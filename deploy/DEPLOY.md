# Deploy do Gestor Financeiro (MEI) em VPS com Docker

Documento do processo realizado em 17/10/2026, migrado em 07/11/2026 do servidor caseiro para um VPS HostGator. Cobre o servidor, as imagens, a operação do dia a dia e a geração do APK Android.

## 1. Visão geral

```
Celular / navegador ──HTTP:80──► [web: nginx] ──/api/──► [api: Quarkus :8080] ──► [db: MariaDB :3306]
                                  serve o Angular           logs em arquivo         dados em volume
```

| Item | Valor |
|---|---|
| Servidor | VPS HostGator (`vpsbr-16427159.vpshostgator.com.br`), Ubuntu 22.04 (kernel 6.8), x86_64, 2 vCPU, 3.8 GB RAM, 98 GB de disco |
| IP público | `143.95.186.188` (acesso vindo de qualquer rede, não só local) |
| SSH | Porta `22022`, usuário `root`, autenticação por chave (`id_ed25519`) |
| Containers | `gestor-web-1` (nginx), `gestor-api-1` (Quarkus 3.35, Java 25), `gestor-db-1` (MariaDB 11) |
| Pasta no servidor | `/root/TCC/deploy` (projeto inteiro em `/root/TCC`) |
| Acesso | `http://143.95.186.188` (internet pública) |
| Build | Feito no PC com Docker Desktop (imagens `linux/amd64`, mesma arquitetura do servidor — sem emulação) e enviado por SSH |

## 2. Preparação do servidor (feita uma vez)

Como root via SSH (porta `22022`):

```bash
# Pastas da aplicação (logs pertencem ao UID 185, usuário do container Java)
mkdir -p /root/TCC/deploy/{keys,logs,db}
chown 185:185 /root/TCC/deploy/logs

# Docker + Compose
curl -fsSL https://get.docker.com | sh
systemctl enable --now docker
```

> O VPS tem 3.8 GB de RAM (bem mais que os 800 MB do servidor caseiro anterior), então o swap manual não é necessário.

### Arquivos do repositório

| Arquivo | Função |
|---|---|
| `backend/gerenciador-api/Dockerfile` | Build multi-stage do backend (Maven → JRE 25) |
| `backend/gerenciador-api/Dockerfile.dockerignore` | Exclusões do contexto de build |
| `Gestor-Financeiro-FRONTEND/Dockerfile` | Build Angular (Node 16) → nginx. Argumento `CONFIGURATION` (padrão `production`) |
| `Gestor-Financeiro-FRONTEND/nginx.conf` | SPA fallback e proxy `/api/` → `api:8080` |
| `Gestor-Financeiro-FRONTEND/src/environments/environment.prod.ts` | `apiUrl: '/api'` (mesma origem, sem CORS) |
| `Gestor-Financeiro-FRONTEND/src/environments/environment.mobile.ts` | `apiUrl: 'http://143.95.186.188/api'` (usado pelo APK) |
| `deploy/docker-compose.yml` | Define os 3 serviços |
| `deploy/.env.example` | Modelo das variáveis secretas |
| `deploy/secrets/` | **Não versionar.** `.env` real, chaves JWT e logs de build |

## 3. Segredos

Gerados no PC e **nunca commitados**:

1. **Chaves JWT** (as do repositório foram descartadas, pois a privada estava commitada):
   `java backend/gerenciador-api/GenerateKeys.java` dentro de `deploy/secrets/` → `privateKey.pem` e `publicKey.pem`.
   No servidor ficam em `/root/TCC/deploy/keys` (montadas somente leitura no container).
2. **`.env`** com `DB_PASSWORD`, `DB_ROOT_PASSWORD`, `APP_CRYPTO_SECRET` (aleatórios, 24+ bytes) e `CORS_ORIGIN`.
   No servidor: `/root/TCC/deploy/.env` com `chmod 600`.

> **Guarde uma cópia do `.env`.** Sem o `APP_CRYPTO_SECRET` os CPF/CNPJ já gravados ficam ilegíveis.

`CORS_ORIGIN` atual: `http://143.95.186.188,http://localhost` (o segundo é a origem do app Android).

## 4. Build das imagens (no PC)

Pré-requisito: Docker Desktop em execução. Como o PC e o VPS são ambos `amd64`, não é preciso emulação/QEMU:

```powershell
cd C:\TCC
docker buildx build --platform linux/amd64 -t gestor-api:latest --load backend/gerenciador-api
docker buildx build --platform linux/amd64 -t gestor-web:latest --load Gestor-Financeiro-FRONTEND
```

Sem emulação os builds ficam bem mais rápidos que no servidor caseiro anterior (arm64).

## 5. Envio e subida

```powershell
docker save gestor-api:latest gestor-web:latest -o imgs.tar
scp -P 22022 imgs.tar deploy\docker-compose.yml deploy\secrets\.env root@143.95.186.188:/root/TCC/deploy/
scp -P 22022 deploy\secrets\privateKey.pem deploy\secrets\publicKey.pem root@143.95.186.188:/root/TCC/deploy/keys/
ssh -p 22022 root@143.95.186.188
```

No servidor:

```bash
cd /root/TCC/deploy
chmod 600 .env keys/*; chmod 644 keys/publicKey.pem; chown -R 185 keys
docker load -i imgs.tar && rm imgs.tar
docker compose up -d
docker compose ps
```

Detalhes da configuração (em `docker-compose.yml`):
- MariaDB: `innodb-buffer-pool-size=64M`, `max-connections=30`, limite 220 MB.
- API: `-Xmx256m`, limite 400 MB, OpenTelemetry desligado (`QUARKUS_OTEL_SDK_DISABLED`), chaves JWT por `file:/keys/...`.
- Web: limite 50 MB, porta 80.
- Esses limites vieram do servidor caseiro (800 MB de RAM); no VPS (3.8 GB) há bastante margem para aumentá-los se necessário.
- As tabelas são criadas pelo Hibernate (`generation=update`) na primeira subida.

## 6. Logs

| O quê | Onde |
|---|---|
| Log do backend (arquivo, rotação 10 MB × 5) | `/root/TCC/deploy/logs/app.log` |
| Log do container (rotação 10 MB × 3) | `docker compose logs -f api` |

```bash
tail -f /root/TCC/deploy/logs/app.log
```

## 7. Reinício automático

Já configurado, nada a fazer: o serviço `docker` está habilitado no boot e os 3 containers têm `restart: unless-stopped`. Depois de reiniciar o servidor, **aguarde 1 a 2 minutos**: a API leva ~70 s para iniciar e nesse intervalo o nginx responde 502.

## 8. Operação

```bash
cd /root/TCC/deploy
docker compose ps                 # estado
docker compose restart api        # reiniciar um serviço
docker compose down               # parar tudo (os dados em ./db permanecem)
docker compose up -d              # subir
free -h                           # memória
```

**Atualizar a aplicação:** refazer o build (seção 4), `docker save`, `scp`, `docker load` e `docker compose up -d`.

**Backup do banco** (recomendado em cron diário):

```bash
cd /root/TCC/deploy
docker compose exec -T db sh -c 'mariadb-dump -ugestor -p"$MARIADB_PASSWORD" gestor_mei' | gzip > /root/backup-$(date +%F).sql.gz
```

Copie também `.env` e `keys/`.

## 9. APK Android (Poco X7 Pro)

O app usa Capacitor. O APK carrega o site empacotado e chama a API em `http://143.95.186.188/api`. Como o servidor agora é um VPS com IP público, o app funciona em **qualquer rede** (Wi-Fi ou dados móveis), não só na rede local.

Ajustes feitos no código:
- `environment.mobile.ts` e a configuração `mobile` em `angular.json`.
- `capacitor.config.ts`: `server.androidScheme = "http"` e `cleartext = true` (necessário para falar HTTP sem TLS; com o esquema `https` padrão o Android bloquearia as chamadas como conteúdo misto).
- CORS da API aceitando `http://localhost`.

Pré-requisitos no PC: Docker Desktop em execução, Android SDK (Android Studio, plataforma 34) e **JDK 17**. O Gradle 7.4.2 do projeto não funciona com o Java 25 (nem o JBR do Android Studio, que é Java 25). O Node do PC (24) também não serve para o Angular 12, por isso o bundle é gerado no Docker.

### Forma rápida: script

```powershell
cd C:\TCC\Gestor-Financeiro-FRONTEND
# -BaixarJdk baixa o Temurin 17 para %TEMP%\jdk17-gestor (nada é instalado no sistema).
# Se já tiver um JDK 17: -JdkHome "<pasta>" ou defina a variável JDK17_HOME.
.\scripts\gerar-apk.ps1 -BaixarJdk -Destino C:\TCC\deploy\GestorFinanceiro-debug.apk
```

O script faz os passos abaixo (build Docker `mobile` → `cap sync` → `gradlew assembleDebug`), grava o commit atual no badge de versão e, com `-Destino`, copia o APK guardando o anterior como `GestorFinanceiro-debug.anterior.apk`. Leva cerca de 3 a 5 minutos.

### Forma manual

```powershell
cd C:\TCC\Gestor-Financeiro-FRONTEND

# 1) Bundle Angular em modo mobile (Node 16 via Docker) e cópia para www/
$sha = (git rev-parse HEAD).Trim()   # aparece no badge de versão (v1.0.0 · abc1234)
docker build --target build --build-arg CONFIGURATION=mobile --build-arg "GIT_SHA=$sha" --build-arg "BUILD_DATE=$(Get-Date -Format s)" -t gestor-web-mobile .
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

**Conferir a versão instalada:** o badge aparece na tela de login e no Perfil (`v1.0.0 · <commit>`). Para mudar o número, altere `APP_VERSION` em `src/app/shared/versao-badge/versao-badge.component.ts`; o commit é preenchido pelo build (`GIT_SHA`).

**Recuperação de senha no APK:** o app chama `/api/auth/esqueci-senha` e `/api/auth/redefinir-senha`. O backend novo precisa estar implantado no servidor e o `.env` dele precisa ter `SMTP_HOST`, `SMTP_PORT`, `SMTP_USER`, `SMTP_PASSWORD` e `SMTP_FROM`; sem isso o código não é enviado.

Observações:
- É um APK **debug** (assinado com a chave de depuração). Para publicar na Play Store é preciso gerar um APK/AAB release com keystore própria.
- App: nome "Finanças MEI", `applicationId` `com.diegomarques.gestorfinanceiro`, `targetSdk` 34 (com `targetSdk` 32 o Play Protect exibia "app criado para uma versão mais antiga do Android"). O ícone está em `android/app/src/main/res/mipmap-*`.
- Se o IP do servidor mudar, edite `environment.mobile.ts` e `CORS_ORIGIN` e refaça os passos.

## 10. Problemas encontrados e soluções

| Sintoma | Causa | Solução |
|---|---|---|
| `mvnw` falha com código 127 no Docker | Quebras de linha CRLF do Windows | Deixou de usar o wrapper |
| `Failed to validate Maven distribution SHA-256` | Download do Maven pelo wrapper rejeitado | Usar a imagem `maven:3.9-eclipse-temurin-25` |
| 502 logo após subir | A API demora ~70 s para iniciar | Aguardar |
| Aviso `Unknown column 'RESERVED'` no log | `db-version=5.5.5` com MariaDB | Inofensivo; tabelas são criadas normalmente |
| Gradle falha com Java 25 | Gradle 7.4.2 só suporta até JDK 17 | Usar JDK 17 em `JAVA_HOME` (`gerar-apk.ps1 -BaixarJdk`) |
| `docker build` falha com "cannot connect to the Docker API" | Docker Desktop parado | Abrir o Docker Desktop e esperar iniciar |

## 11. Pendências de segurança

- Trocar a senha do root periodicamente, manter autenticação por chave SSH e definir `PermitRootLogin prohibit-password` no `sshd_config` (o servidor agora é acessível pela internet pública, não só pela rede local — isso é ainda mais importante que antes).
- O tráfego é HTTP puro; como o VPS tem IP público (acesso de qualquer lugar da internet), considerar HTTPS (proxy reverso com certificado, por exemplo via Let's Encrypt/Caddy) e revisar o CORS.
- A chave JWT antiga e `private-key.pem` ainda estão na árvore do backend; remova-as do Git.
- Considerar um firewall (`ufw`) liberando só as portas necessárias (80, 443 e a porta SSH customizada `22022`).

## 12. Pipelines de deploy (GitHub Actions)

Workflows em `.github/workflows/`: `backend.yml` e `frontend.yml`. Cada um só dispara quando mudam arquivos da sua pasta (ou manualmente, em *Run workflow*).

| Evento | O que acontece |
|---|---|
| Pull request | Backend: `mvn verify`. Frontend: `ng build --configuration production`. |
| Push na `main` | Testa/compila → constrói a imagem `linux/amd64` e publica no GHCR (`ghcr.io/<dono>/gestor-api` e `gestor-web`, tags `latest` e SHA) → o runner do servidor baixa a imagem, aplica a tag local `gestor-api:latest`/`gestor-web:latest`, roda `docker compose up -d <serviço>` e confere se responde. |

Como o VPS tem IP público, o runner self-hosted poderia ser trocado por deploy direto via SSH nos workflows; por ora ele continua self-hosted por simplicidade (mesmo fluxo de antes).

**Configuração (uma vez):**
1. Repositório no GitHub com a raiz em `C:\TCC` (monorepo) e a branch `main`.
2. No servidor, usuário não-root `runner` no grupo `docker`, e o runner instalado em *Settings → Actions → Runners → New self-hosted runner* (Linux x64), com a label extra `gestor`. Instale como serviço (`./svc.sh install && ./svc.sh start`).
3. O usuário do runner precisa ler `/root/TCC/deploy/.env` e usar `/root/TCC/deploy/docker-compose.yml`.
4. *Settings → Environments → production*: opcionalmente exigir aprovação manual para o deploy.
5. Adicione ao `/root/TCC/deploy/.env` as variáveis de e-mail da recuperação de senha: `SMTP_HOST`, `SMTP_PORT`, `SMTP_USER`, `SMTP_PASSWORD`, `SMTP_FROM`.
6. Para o servidor/GHCR, os pacotes criados pelo workflow ficam privados; o login usa o `GITHUB_TOKEN` do próprio job.

O `docker-compose.yml` não muda: continua usando `gestor-api:latest` e `gestor-web:latest`, então o deploy manual (seção 5) segue funcionando. **Reversão:** `docker tag ghcr.io/<dono>/gestor-api:<sha-antigo> gestor-api:latest && docker compose up -d api`.
