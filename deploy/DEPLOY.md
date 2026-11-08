# Deploy do Gestor Financeiro (MEI) em VPS com Docker

Documento do processo realizado em 17/10/2026, migrado em 07/11/2026 do servidor caseiro para um VPS HostGator. Cobre o servidor, as imagens, a operação do dia a dia e a geração do APK Android.

## 1. Visão geral

```
Celular / navegador ──HTTP:80──► [web: nginx] ──/api/──► [api: Quarkus :8080] ──► [db: MariaDB :3306]
                                  serve o Angular           logs em arquivo         dados em volume
                                                             └──► [smtp: Postfix] ──► internet (recuperação de senha)
```

| Item | Valor |
|---|---|
| Servidor | VPS HostGator (`vpsbr-16427159.vpshostgator.com.br`), Ubuntu 22.04 (kernel 6.8), x86_64, 2 vCPU, 3.8 GB RAM, 98 GB de disco |
| IP público | `143.95.186.188` (acesso vindo de qualquer rede, não só local) |
| SSH | Porta `22022`, usuário `root`, autenticação por chave (`id_ed25519`) |
| Containers | `web` (nginx), `api` (Quarkus 3.35, Java 25), `db` (MariaDB 11), `smtp` (Postfix, relay de e-mail) |
| Pasta no servidor | `/root/TCC` tem o **projeto inteiro** (código-fonte + deploy); o compose roda em `/root/TCC/deploy` |
| Acesso | `http://143.95.186.188` (internet pública) |
| Build | **Direto na VPS**, a partir do código em `/root/TCC` (`docker compose build`). Não depende mais do PC/Docker Desktop. |

> Todo o processo de build + deploy agora acontece dentro da pasta `/root/TCC` da própria VPS: não é mais necessário buildar imagens no PC e enviá-las por `scp`. Para atualizar, basta levar o código novo para `/root/TCC` (via `scp`/`rsync`/`git pull`) e rodar `docker compose build && docker compose up -d` lá dentro (seção 4).

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
| `deploy/docker-compose.yml` | Define os 4 serviços (`db`, `api`, `web`, `smtp`); `api` e `web` têm `build:` apontando para `../backend/gerenciador-api` e `../Gestor-Financeiro-FRONTEND` (código-fonte um nível acima de `deploy/`) |
| `deploy/.env.example` | Modelo das variáveis secretas |
| `deploy/secrets/` | **Não versionar.** Cópia de referência do `.env`, chaves JWT e logs de build |
| `deploy/.env` | **O `.env` que o `docker compose` lê de fato** (precisa estar ao lado do `docker-compose.yml`, não dentro de `secrets/`) |

## 3. Segredos

Gerados uma vez e **nunca commitados** (podem ser gerados direto na VPS, já que tudo acontece lá agora):

1. **Chaves JWT** (as do repositório foram descartadas, pois a privada estava commitada):
   `java backend/gerenciador-api/GenerateKeys.java` → `privateKey.pem` e `publicKey.pem` em `/root/TCC/deploy/keys` (montadas somente leitura no container).
2. **`.env`** em `/root/TCC/deploy/.env` (`chmod 600`), com:
   - `DB_PASSWORD`, `DB_ROOT_PASSWORD`, `APP_CRYPTO_SECRET` (aleatórios, 24+ bytes)
   - `CORS_ORIGIN`
   - `SMTP_DOMAIN` / `SMTP_FROM` (usados pelo relay de e-mail próprio, seção 9)

> **Guarde uma cópia do `.env`.** Sem o `APP_CRYPTO_SECRET` os CPF/CNPJ já gravados ficam ilegíveis.
>
> ⚠️ O `docker compose` só lê `.env` se ele estiver **no mesmo diretório do `docker-compose.yml`** (`deploy/.env`). Guardar o `.env` só em `deploy/secrets/.env` faz a API subir com variáveis vazias e travar na validação de config (já aconteceu — ver seção 10).

`CORS_ORIGIN` atual: `http://143.95.186.188,http://localhost` (o segundo é a origem do app Android).

## 4. Build e subida (direto na VPS)

Todo o build agora roda **na própria VPS**, usando o código-fonte que já está em `/root/TCC` (não precisa mais de Docker Desktop no PC nem de `docker save`/`scp` de imagens).

**Para atualizar o código:** envie as mudanças para `/root/TCC` no servidor (ex.: `scp -P 22022 -r <pasta> root@143.95.186.188:/root/TCC/<pasta>`, ou configure `git pull` lá se preferir versionar direto no servidor).

**Para buildar e subir** (via SSH, porta `22022`):

```bash
ssh -p 22022 root@143.95.186.188
cd /root/TCC/deploy
docker compose build      # builda gestor-api e gestor-web a partir do código em ../backend e ../Gestor-Financeiro-FRONTEND
docker compose up -d
docker compose ps
```

Como o runner e o servidor já são a mesma máquina `amd64`, não há emulação/QEMU envolvida — o build é bem mais rápido que no servidor caseiro anterior (arm64).

Detalhes da configuração (em `docker-compose.yml`):
- MariaDB: `innodb-buffer-pool-size=64M`, `max-connections=30`, limite 220 MB.
- API: `-Xmx256m`, limite 400 MB, OpenTelemetry desligado (`QUARKUS_OTEL_SDK_DISABLED`), chaves JWT por `file:/keys/...`.
- Web: limite 50 MB, porta 80.
- SMTP: Postfix (`boky/postfix`), limite 64 MB, só na rede interna do Docker (sem porta exposta).
- Esses limites vieram do servidor caseiro (800 MB de RAM); no VPS (3.8 GB) há bastante margem para aumentá-los se necessário.
- As tabelas são criadas pelo Hibernate (`generation=update`) na primeira subida.

> Depois de recriar o container `api` (ex.: `docker compose up -d` após um rebuild), se o `web` tiver sido iniciado **antes** e não for recriado junto, ele pode ficar com o IP antigo do `api` em cache (DNS do nginx). Se a API responder 502/503 mesmo depois de subir, rode `docker compose restart web`.

## 6. Logs

| O quê | Onde |
|---|---|
| Log do backend (arquivo, rotação 10 MB × 5) | `/root/TCC/deploy/logs/app.log` |
| Log do container (rotação 10 MB × 3) | `docker compose logs -f api` |

```bash
tail -f /root/TCC/deploy/logs/app.log
```

## 7. Reinício automático

Já configurado, nada a fazer: o serviço `docker` está habilitado no boot e os 4 containers têm `restart: unless-stopped`. Depois de reiniciar o servidor, **aguarde 1 a 2 minutos**: a API leva ~70 s para iniciar e nesse intervalo o nginx responde 502.

## 8. Operação

```bash
cd /root/TCC/deploy
docker compose ps                 # estado
docker compose restart api        # reiniciar um serviço
docker compose down               # parar tudo (os dados em ./db permanecem)
docker compose up -d              # subir
free -h                           # memória
```

**Atualizar a aplicação:** enviar o código novo para `/root/TCC` (seção 4) e rodar `docker compose build && docker compose up -d` (lembre de `docker compose restart web` se o `api` for recriado e o `web` não).

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

**Recuperação de senha no APK:** o app chama `/api/auth/esqueci-senha` e `/api/auth/redefinir-senha`. O e-mail com o código é enviado pelo serviço `smtp` (Postfix) do `docker-compose.yml` — ver seção 9.1.

### 9.1 Relay de e-mail (recuperação de senha)

A API só usa SMTP para enviar o código de redefinição de senha (`ResetMailService`); falhas de envio são apenas logadas, nunca quebram a aplicação.

Em vez de depender de um provedor externo (Gmail etc.), o `docker-compose.yml` sobe um **Postfix próprio** (`boky/postfix`) como serviço `smtp`, que entrega e-mails direto ao servidor de destino pela porta 25 de saída (confirmada aberta nesta VPS). Ele só escuta na rede interna do Docker, sem porta exposta na internet, e não exige usuário/senha (a API é o único cliente, dentro da rede do compose).

Variáveis no `.env`:
- `SMTP_DOMAIN` — domínio autorizado a enviar pelo relay (`ALLOWED_SENDER_DOMAINS` do Postfix).
- `SMTP_FROM` — remetente usado pela API (`quarkus.mailer.from`).

> ⚠️ **Entregabilidade:** como `SMTP_DOMAIN`/`SMTP_FROM` usam um domínio fictício (`gestor-mei.local`, sem DNS/MX/SPF/PTR configurados), provedores grandes (Gmail, Outlook etc.) tendem a **rejeitar ou jogar em spam** esses e-mails. Para entrega confiável, registre um domínio real, aponte o DNS reverso (PTR) do IP `143.95.186.188` para esse domínio e configure SPF/DKIM — ou troque o relay por um serviço transacional (SendGrid, SES, Mailgun...).

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
| API em loop de restart: `NoSuchElementException: quarkus.mailer.host is defined as the empty String` | `.env` só existia em `deploy/secrets/.env`; o `docker compose` não leu (precisa estar em `deploy/.env`) e passou `QUARKUS_MAILER_HOST=""` | Copiar/manter o `.env` em `deploy/.env` (ao lado do `docker-compose.yml`) |
| API sobe mas o nginx continua respondendo 502/503 | `web` resolveu o IP do `api` antes dele ser recriado num rebuild e ficou com DNS cacheado | `docker compose restart web` |

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
5. O `.env` em `/root/TCC/deploy/.env` já tem `SMTP_DOMAIN`/`SMTP_FROM` para o relay próprio (seção 9.1); não é mais preciso configurar um provedor externo.
6. Para o servidor/GHCR, os pacotes criados pelo workflow ficam privados; o login usa o `GITHUB_TOKEN` do próprio job.

O `docker-compose.yml` não muda: continua usando `gestor-api:latest` e `gestor-web:latest`, então o deploy manual (seção 4) segue funcionando. **Reversão:** `docker tag ghcr.io/<dono>/gestor-api:<sha-antigo> gestor-api:latest && docker compose up -d api`.

> Alternativa mais simples (já que o build agora funciona direto na VPS): trocar o workflow de "builda na nuvem + publica no GHCR + runner baixa a imagem" por um deploy via SSH que só copia o código novo para `/root/TCC` e roda `docker compose build && docker compose up -d` no servidor — dispensando GHCR e builds `linux/amd64` no GitHub-hosted runner. Não implementado ainda; é só uma opção caso o runner self-hosted dê trabalho de manter.
