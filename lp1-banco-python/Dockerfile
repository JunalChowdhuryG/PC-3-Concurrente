# 1. Usar una imagen base de Python
FROM python:3.10-slim
# 2. Establecer el directorio de trabajo
WORKDIR /app
# 3. Instalar dependencias para compilar psycopg2
RUN apt-get update && apt-get install -y gcc libpq-dev && rm -rf /var/lib/apt/lists/*
# 4. Copiar y instalar las librerías de Python
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt
# 5. Copiar el código del servicio
COPY service.py .
# 6. Comando para ejecutar el servicio
CMD ["python3", "service.py"]