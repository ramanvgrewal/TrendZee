FROM node:20-slim
WORKDIR /app
COPY package*.json ./
RUN npm install
COPY . .
RUN npm run build
EXPOSE 8081
CMD ["npm", "run", "preview", "--", "--host", "0.0.0.0", "--port", "8081"]
