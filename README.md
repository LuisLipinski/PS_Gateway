# PS_Gateway

API Gateway do **My Pet Admin** e futura borda única entre o frontend e os microsserviços.

## Responsabilidades

- autenticação de borda com JWT;
- roteamento explícito e allowlisted;
- deny-by-default;
- sanitização de headers internos enviados pelo cliente;
- propagação segura de `X-Correlation-Id`;
- CORS centralizado;
- health/info/version e observabilidade de borda.

O Gateway **não implementa regra de negócio** de Empresa, User, Contrato, Login ou Orchestrator.

## Stack

- Java 25 LTS;
- Spring Boot 4.1.1;
- Spring Cloud 2025.1.3;
- Spring Cloud Gateway Server WebFlux;
- Spring Security OAuth2 Resource Server;
- Reactor Netty;
- Actuator;
- Docker;
- JaCoCo com LINE >= 90% e BRANCH >= 70%.

## Rotas da fundação

Rotas públicas encaminhadas ao PS_Login:

- `POST /api/auth/login` -> `/auth/login`;
- `POST /api/auth/activation` -> `/auth/activation`;
- `POST /api/auth/password/forgot` -> `/auth/password/forgot`;
- `POST /api/auth/password/reset` -> `/auth/password/reset`.

Rota autenticada:

- `POST /api/auth/password/change` -> `/auth/password/change`.

Rotas locais públicas:

- `GET /actuator/health`;
- `GET /actuator/info`;
- `GET /version`.

Qualquer outra rota permanece negada até ser explicitamente adicionada.

## Segurança transitória

O PS_Login ainda emite HS256. O Gateway valida o mesmo JWT nesta fase com:

- `sub` = userId;
- `empresaId`;
- `roles`;
- `iss`, `iat`, `exp`, `jti`.

`JWT_SECRET_KEY` deve ser Base64 válido representando pelo menos 32 bytes. Em produção, o secret é obrigatório e deve ser coordenado com PS_Login e validadores atuais. A evolução alvo é assinatura assimétrica + JWKS.

Headers externos reservados são removidos antes do downstream, incluindo `X-Internal-Key`, `X-Actor-User-Id`, `X-Onboarding-Id`, `X-User-Id`, `X-Empresa-Id` e `X-Roles`.

## Variáveis de ambiente — produção

```env
SPRING_PROFILES_ACTIVE=prod
PS_LOGIN_URL=https://ps-login.onrender.com
JWT_SECRET_KEY=<base64-256-bits-ou-mais>
JWT_ISSUER=ps-login
ALLOWED_ORIGINS=https://<frontend-futuro>
```

O Gateway não possui banco próprio.

Não configure `PORT` manualmente no Render; o ambiente fornece a porta.

## Próximo PR

O onboarding público será implementado separadamente com:

```text
POST /api/onboardings
Idempotency-Key: <UUID>
        ↓
PS_Gateway
        ↓ X-Onboarding-Id + X-Internal-Key
PS_Orchestrator /internal/onboardings
```

O browser nunca receberá `X-Internal-Key`.
