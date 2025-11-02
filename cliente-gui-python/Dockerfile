# 1. Usar una imagen base de Python
FROM python:3.10-slim

# 2. Instalar dependencias del sistema, incluyendo python3-tk para la GUI
RUN apt-get update && apt-get install -y python3-tk

# 3. Establecer el directorio de trabajo
WORKDIR /app

# 4. Copiar y instalar las librerías de Python
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

# 5. Copiar el código de la aplicación
COPY client-gui.py .

# 6. Comando para ejecutar la aplicación
CMD ["python3", "client-gui.py"]