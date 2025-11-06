# Proyecto Shibasito - Sistema Bancario Distribuido

Este proyecto implementa un sistema distribuido de pagos y préstamos ("Shibasito"), simulando una arquitectura bancaria moderna. El sistema utiliza un Middleware Orientado a Mensajes (RabbitMQ) para desacoplar los servicios de backend de los clientes frontend, permitiendo la integración de 4 lenguajes de programación y 2 motores de bases de datos distintos.

Este proyecto fue desarrollado como parte del curso de Programación Concurrente y Distribuida.

## Características

  * **Arquitectura Híbrida:** El backend (servicios y BDs) corre en **Docker**, mientras que el middleware (RabbitMQ) y los clientes (GUI y Móvil) corren **localmente**.
  * **Políglota (4 Lenguajes):**
      * **Python (LP1):** Servicio de Banco.
      * **Node.js (LP2):** Servicio de RENIEC.
      * **Java (LP3):** Cliente GUI de Escritorio (JavaFX).
      * **Kotlin (LPx):** Cliente Móvil (Android).
  * **Bases de Datos Múltiples (2):**
      * **PostgreSQL:** Para la base de datos transaccional del banco (BD1).
      * **MySQL:** Para la base de datos de consulta de ciudadanos (BD2).
  * **Comunicación Asíncrona:** Todas las operaciones (consultas, transferencias) se realizan mediante el patrón Petición/Respuesta (RPC) sobre RabbitMQ.
  * **Orquestación:** El backend completo se levanta con un solo comando gracias a `docker-compose`.
  * **Reportes en App:** La app móvil muestra el saldo y el historial en tiempo real, e incluye una pantalla de configuración de IP y un recibo de transferencia animado.

-----

## Arquitectura del Sistema

El proyecto utiliza una arquitectura híbrida:

  * **Host (Tu PC):** Ejecuta el servidor RabbitMQ y las aplicaciones cliente (Java GUI y Kotlin App).
  * **Contenedores Docker:** Ejecutan los servicios de backend (LP1, LP2) y sus bases de datos (BD1, BD2).

La comunicación de red es la clave:

1.  **Clientes (Host) -\> RabbitMQ (Host):** Los clientes Java y Kotlin se conectan a RabbitMQ usando la **IP de tu red local** (ej. `192.168.0.9`).
2.  **Servicios (Docker) -\> RabbitMQ (Host):** Los servicios LP1 (Python) y LP2 (Node.js) usan la dirección DNS especial **`host.docker.internal`** para encontrar el servidor RabbitMQ que corre en tu PC.

(Para el diagrama de arquitectura D2 completo, consulta el Apéndice A).

-----

## Prerrequisitos (Instalación en el Host)

Antes de ejecutar el proyecto, necesitas tener el siguiente software instalado en tu máquina (Windows):

1.  **RabbitMQ Server (Local):**
      * Descarga e instala **Erlang** (requisito de RabbitMQ).
      * Descarga e instala **RabbitMQ Server**.
      * Verifica que puedas acceder a `http://localhost:15672` e iniciar sesión con `guest` / `guest`.
2.  **Docker Desktop:**
      * Necesario para levantar los servicios de backend y las bases de datos.
3.  **Java JDK 17+ y Maven:**
      * Necesario para compilar y ejecutar el cliente `cliente-gui-java`.
4.  **Android Studio:**
      * Necesario para compilar y ejecutar el cliente `cliente-movil-kotlin` en un emulador o dispositivo.

-----

## Instrucciones de Ejecución

Sigue estos pasos en orden para levantar el sistema completo.

### Fase 1: Configurar el Host (RabbitMQ y Red)

Este es el paso más importante para permitir que los clientes locales se comuniquen con el backend.

**1. Deshabilitar la Seguridad de `guest` en RabbitMQ:**

  * Presiona `Windows + R`, escribe `%APPDATA%\RabbitMQ` y presiona Enter.
  * Crea (o edita) un archivo llamado `rabbitmq.conf`.
  * Añade la siguiente línea y guarda el archivo:
    ```ini
    loopback_users = none
    ```

**2. Reiniciar el Servicio de RabbitMQ:**

  * Presiona `Windows + R`, escribe `services.msc` y presiona Enter.
  * Busca el servicio `RabbitMQ` en la lista.
  * Haz clic derecho sobre él y selecciona **"Reiniciar"**.

**3. Configurar el Firewall de Windows:**

  * Busca y abre "Windows Defender Firewall con seguridad avanzada".
  * Ve a "Reglas de entrada" y haz clic en "Nueva regla...".
  * **Tipo de Regla:** Puerto
  * **Protocolo y Puertos:** TCP. Puertos locales específicos: `5672, 15672`
  * **Acción:** Permitir la conexión
  * **Perfil:** Deja todas las casillas (Privado, Público) marcadas.
  * **Nombre:** `RabbitMQ Local`

**4. Obtener tu IP Local:**

  * Abre una terminal (CMD) y escribe `ipconfig`.
  * Busca tu "Adaptador de LAN inalámbrica Wi-Fi" o "Adaptador de Ethernet" y anota tu **`Dirección IPv4`** (ej. `192.168.0.9`).

### Fase 2: Configurar y Ejecutar el Backend (Docker)

1.  **Configurar Conexión del Backend:**

      * Verifica que los archivos `lp1-banco-python/service.py` y `lp2-reniec-nodejs/index.js` estén usando `host.docker.internal` para la `RABBITMQ_URL`:

    <!-- end list -->

    ```python
    # En service.py
    RABBIT_URL = os.getenv('RABBITMQ_URL', 'amqp://guest:guest@host.docker.internal:5672')
    ```

    ```javascript
    // En index.js
    const RABBITMQ_URL = process.env.RABBITMQ_URL || 'amqp://guest:guest@host.docker.internal:5672';
    ```

2.  **Iniciar Docker:**

      * Abre Docker Desktop y asegúrate de que esté corriendo.

3.  **Levantar Contenedores:**

      * Abre una terminal en la raíz del proyecto (`proyecto-shibasito/`).
      * Destruye cualquier contenedor antiguo para asegurar una instalación limpia:
        ```bash
        docker-compose down -v
        ```
      * Construye y levanta todo el backend:
        ```bash
        docker-compose up --build
        ```
      * Espera a que los logs de `lp1-banco-servicio` y `lp2-reniec-servicio` se estabilicen y muestren los mensajes de `[*] Esperando mensajes...`.

### Fase 3: Ejecutar los Clientes Locales

**A. Cliente GUI (JavaFX):**

1.  **Configurar IP:**
      * Abre el proyecto `cliente-gui-java/` en tu editor.
      * Edita el archivo `src/main/java/com/shibasito/Main.java`.
      * Cambia la variable `RABBITMQ_HOST_IP` por la IP que obtuviste en la Fase 1 (Paso 4):
    <!-- end list -->
    ```java
    // ...
    private static final String RABBITMQ_HOST_IP = "192.168.0.9"; // <-- ¡PON TU IP AQUÍ!
    // ...
    ```
2.  **Ejecutar:**
      * Abre una **nueva terminal** y navega a la carpeta `cliente-gui-java/`.
      * Ejecuta la aplicación usando Maven:
        ```bash
        mvn clean javafx:run
        ```
      * La GUI aparecerá y podrás probar todas las operaciones.

**B. Cliente Móvil (Kotlin):**

1.  **Ejecutar:**
      * Abre el proyecto `cliente-movil-kotlin/` en Android Studio.
      * Conecta un dispositivo Android o inicia un Emulador.
      * Presiona el botón "Run" (▶).
2.  **Configurar IP (En la App):**
      * La primera vez que se ejecute la app, te pedirá la "Configuración de Red".
      * Ingresa la misma IP de tu PC (ej. `192.168.0.9`) y presiona "Guardar".
3.  **Probar:**
      * La app te llevará al Login. Ingresa un `idCliente` (ej. `CL001`).
      * Serás llevado a la pantalla principal donde podrás ver tu saldo, historial y realizar transferencias.
      * *Nota: Para cambiar la IP después, ve a la pantalla principal y presiona "Cambiar IP".*

-----

## 🛠️ Stack Tecnológico

| Categoría | Tecnología | Rol en el Proyecto |
| :--- | :--- | :--- |
| **Middleware** | RabbitMQ Server 3.13 | Broker AMQP local (en Host). |
| **Contenerización**| Docker-Compose | Orquestación de servicios backend y BDs. |
| **Backend (LP1)** | Python 3.10 | Lógica de negocio del Banco (Saldos, Préstamos). |
| **Backend (LP2)** | Node.js (v18) | Lógica de negocio de RENIEC (Validación). |
| **Base de Datos 1**| PostgreSQL 16 | Almacenamiento transaccional de Cuentas (BD1). |
| **Base de Datos 2**| MySQL 8.0 | Almacenamiento de datos de ciudadanos (BD2). |
| **Cliente GUI (LP3)**| Java 17 (JavaFX) | Interfaz de escritorio para operaciones. |
| **Cliente Móvil (LPx)**| Kotlin (Android) | Interfaz móvil para simulación de pagos. |

-----
