# 1. Usar una imagen base oficial de Node.js
FROM node:18-slim

# 2. Establecer el directorio de trabajo
WORKDIR /app

# 3. Copiar el package.json
COPY package*.json ./

# 4. Instalar las dependencias
RUN npm install

# 5. Copiar el resto del código
COPY index.js .

# 6. Comando para ejecutar
CMD [ "node", "index.js" ]