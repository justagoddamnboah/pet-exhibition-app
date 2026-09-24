## Запуск

```powershell
Copy-Item .env.example .env
docker compose up -d postgres
.\mvnw.cmd -pl library-service spring-boot:run "-Dspring-boot.run.profiles=session-auth"
```

Сервис доступен по адресу `http://localhost:8080`. Страница входа находится по адресу `http://localhost:8080/login`. Учебные пользователи:

- `student/student` с ролью `READER` для чтения;
- `editor/editor` с ролями `READER` и `EDITOR` для чтения и изменения.

Endpoint `GET /csrf` возвращает CSRF token для PowerShell-сценария и доверенного браузерного клиента. Изменяющие запросы требуют одновременно session cookie, подходящую роль и заголовок `X-XSRF-TOKEN`.

## Проверка

```powershell
.\mvnw.cmd test
docker compose exec postgres psql -U course -d library -c "select * from flyway_schema_history;"
```

Для остановки инфраструктуры выполните `docker compose down`. Данные сохраняются в именованном томе; команда `docker compose down -v` удалит их и нужна только для осознанного повторения работы с чистой базой.
