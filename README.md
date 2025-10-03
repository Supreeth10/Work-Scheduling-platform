# 🚚 Work-Scheduling Platform

A lightweight logistics dispatcher where **drivers** start shifts and receive **loads** (pickup → drop-off), and **admins** create loads and track status.  
The system auto-dispatches loads to eligible on-shift drivers and tracks progress through stops. For detailed information about the project, refer to the PDF file `SystemFlow.pdf`.

- **Backend:** Spring Boot (Java 21)
- **Frontend:** React + Vite (served via Nginx)
- **Database:** Postgres + PostGIS
- **Orchestration:** Docker Compose

---

## 📦 How to Run

From the **project root directory** (where `docker-compose.yml` is located):

```bash
# Start the application
docker compose up -d --build

# Stop and remove containers + volumes
docker compose down -v
