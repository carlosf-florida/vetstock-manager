# Instalación y arranque en Windows

Comprobado en Windows 11 con: Temurin **JDK 21.0.8**, Maven Wrapper (descarga
Maven 3.9.15 automáticamente, no hace falta instalar Maven), Node.js 24,
Docker Desktop 28 y MySQL 8.4 en contenedor.

## 1. Requisitos

| Programa | Para qué | Comprobar |
|---|---|---|
| JDK 21 (Temurin recomendado) | Backend | `java -version` → 21 |
| Variable `JAVA_HOME` apuntando al JDK 21 | Maven Wrapper | `echo $env:JAVA_HOME` |
| Node.js 18 o superior | Frontend | `node -v` |
| Docker Desktop **o** MySQL 8 instalado | Base de datos | `docker --version` / `mysql --version` |

## 2. Opción A — Todo con Docker (lo más sencillo)

Desde la carpeta del proyecto:

```powershell
copy .env.example .env      # opcional: cambia las claves de ejemplo
docker compose up -d --build
```

- Aplicación: <http://localhost:5173> · API: <http://localhost:8080>
- La primera vez, MySQL ejecuta `001_esquema_inicial.sql` y `008_vistas_powerbi.sql`.
- También arranca Odoo (opcional). Si no lo necesitas: `docker compose up -d db-mysql backend frontend`.
- Apagar sin borrar datos: `docker compose down`. Empezar de cero: `docker compose down -v`.

## 3. Opción B — Desarrollo (backend y frontend en tu equipo)

### 3.1 Base de datos

Con Docker, levanta solo MySQL:

```powershell
docker compose up -d db-mysql
```

Con un MySQL instalado en Windows, carga el esquema desde `cmd` (no desde
PowerShell, donde `<` no funciona):

```bat
mysql -u root -p < database\migrations\001_esquema_inicial.sql
mysql -u root -p vetstock < database\migrations\008_vistas_powerbi.sql
```

### 3.2 Variables de entorno (si no usas root/root)

```powershell
$env:DB_URL = "jdbc:mysql://localhost:3306/vetstock?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Europe/Madrid"
$env:DB_USER = "root"
$env:DB_PASSWORD = "tu-clave"
$env:PAGOS_SECRETO_WEBHOOK = "una-clave-larga"
```

Todas las variables están explicadas en [`.env.example`](../.env.example). Si
no defines ninguna, se usan los valores de demostración.

### 3.3 Backend (puerto 8080)

```powershell
cd backend
.\mvnw.cmd spring-boot:run
```

### 3.4 Frontend (puerto 5173)

En otra terminal:

```powershell
cd frontend
npm install
npm run dev
```

Abre <http://localhost:5173> y entra con una cuenta de demostración (ver README).

## 4. Reiniciar los datos de la demo

`001_esquema_inicial.sql` **borra y vuelve a crear** todas las tablas con los
datos ficticios. Úsalo solo con datos de prueba. Con el MySQL de Docker:

```powershell
docker cp database\migrations\001_esquema_inicial.sql vetstock-mysql:/tmp/001.sql
docker cp database\migrations\008_vistas_powerbi.sql vetstock-mysql:/tmp/008.sql
docker exec vetstock-mysql sh -c "mysql --default-character-set=utf8mb4 -uroot -proot < /tmp/001.sql"
docker exec vetstock-mysql sh -c "mysql --default-character-set=utf8mb4 -uroot -proot vetstock < /tmp/008.sql"
```

Los pedidos de ejemplo enviados a firma caducan a las 72 h de cargar los datos.
Antes de una demo conviene reiniciarlos.

## 5. Actualizar una base de datos que ya tenías (sin perder datos)

Aplica en orden las migraciones que te falten (todas se pueden repetir sin
romper nada). Por ejemplo, con Docker:

```powershell
foreach ($m in "006_modificacion_pedidos","007_caducidad_reservas","008_vistas_powerbi") {
  docker cp "database\migrations\$m.sql" "vetstock-mysql:/tmp/$m.sql"
  docker exec vetstock-mysql sh -c "mysql --default-character-set=utf8mb4 -uroot -proot vetstock < /tmp/$m.sql"
}
```

Atención: con la regla nueva, los pedidos antiguos enviados a firma y sin pagar
caducarán (se cancelan a las 72 h de su fecha de envío o de creación). Además,
los pedidos que se pagaron antes de firmar con la versión anterior ya no
pueden pagarse de nuevo; solo les falta la firma.

## 6. Pruebas

```powershell
cd backend
.\mvnw.cmd test
```

Las pruebas de integración crean y usan su **propia** base `vetstock_test` (no
tocan `vetstock`). Si MySQL no está arrancado, se omiten y solo se ejecutan las
pruebas unitarias. Detalles en [pruebas.md](pruebas.md).

## 7. IntelliJ IDEA

Desde la terminal, el proyecto compila y pasa las pruebas con el wrapper y el
JDK 21. El error de importación que apareció en su día en IntelliJ
(*"Unable to create injector / No implementation for ArtifactHandlerManager was
bound"*) es un problema interno del Maven que usa el IDE, no del código.
**No se ha reproducido ni reparado en esta revisión** (no se puede comprobar
el IDE desde aquí). Si vuelve a aparecer:

1. *Settings → Build, Execution, Deployment → Build Tools → Maven → Maven home path*: elige **Use Maven wrapper**.
2. En esa misma pantalla, *Importing → JDK for importer*: el JDK 21.
3. *File → Project Structure → Project SDK*: 21.
4. *File → Invalidate Caches / Restart* y vuelve a cargar el proyecto Maven.

No añadas dependencias (como Guice) para tapar ese error: no las necesita el proyecto.

## 8. Problemas frecuentes

| Síntoma | Causa probable |
|---|---|
| `Communications link failure` al arrancar | MySQL no está arrancado o el puerto 3306 está ocupado |
| `Unknown column 'motivo_cancelacion'` | Base antigua: aplica la migración 007 (apartado 5) |
| El frontend muestra «Debes iniciar sesión» en bucle | El backend no está en el puerto 8080 (ajusta `BACKEND_URL`) |
| Acentos mal en los datos | Carga los `.sql` con `--default-character-set=utf8mb4` |
