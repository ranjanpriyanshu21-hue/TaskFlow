# TaskFlow

Task and project management with a Kanban board, collaboration and real-time updates.
Java 17, Spring Boot 3 (Web, Security, Data JPA, WebSocket/STOMP), Thymeleaf, H2.

## Run
    mvn spring-boot:run
Open http://localhost:8080, register two users (use a private window for the second) and create a project.
The data is stored in ./data (H2 file) and uploads in ./uploads.

## Features
- Kanban board: To do / In progress / In review / Done, drag and drop (or change status in the task dialog)
- Projects with members; only members can see a project; the owner adds people by username
- Tasks: title, description, priority, deadline, assignee; overdue and due-soon highlighting; progress bar; "only my tasks" filter
- Collaboration: comments and file attachments per task
- Reminders: a scheduler checks every minute and notifies the assignee once when a task is due within
  `app.reminder.lead-hours` (default 24) or overdue
- Real-time: board changes and notifications are pushed over WebSocket (STOMP) to everyone connected

## Layout
- `model`, `repository`: JPA entities and Spring Data repositories
- `service`: business rules (TaskService, ProjectService, NotificationService, ReminderScheduler, FileStorageService)
- `controller`: ApiController (REST /api/**), PageController and AuthController (HTML pages)
- `config`: SecurityConfig (form login, BCrypt, CSRF), WebSocketConfig
- `resources/templates`, `resources/static`: Thymeleaf pages, CSS and JavaScript

## Production notes
Switch H2 to PostgreSQL/MySQL in application.properties, put the app behind HTTPS, and send reminders by e-mail
(add spring-boot-starter-mail and call it from ReminderScheduler) if you need them outside the app.
