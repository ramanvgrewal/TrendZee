# TrendZee

**TrendZee** is an AI-powered fashion micro-trend analysis platform. It continuously gathers and processes social signals (from platforms like YouTube and Pinterest), evaluates engagement metrics (likes, comments, hashtags), and utilizes Large Language Models (LLMs via Groq) to extract and predict emerging fashion trends, with a focus on specific markets like India. 

The application provides a comprehensive dashboard to visualize these trends, making it easier to discover actionable insights in the fast-moving fashion industry.

## Project Architecture

TrendZee is a full-stack application composed of three main microservices:
1. **Frontend (`TrendZee frontend`)**: A modern React/Vite web dashboard featuring interactive charts (Recharts) and UI components (Radix UI) to visualize trend intelligence. Runs on port `8081`.
2. **Java Backend (`TrendZY`)**: A Spring Boot application that manages data APIs, integrations (like YouTube API), and core backend logic. Runs on port `8080`.
3. **Python AI Worker (`trendzy-ai-worker`)**: A Kafka-driven AI processor that consumes raw social signals, analyzes them using Groq LLMs (e.g., Llama 3), and updates the database with structured trend insights.
4. **Infrastructure**: MongoDB (Data Storage) and Kafka (Event Streaming / Message Broker).

## Requirements
- Docker
- Docker Compose

## Quick Start (Local Development)

1. **Configure Environment**
   A `.env` file is located at the root of the project. Fill in your necessary API keys (`GROQ_API_KEY`, `YOUTUBE_API_KEY`).

2. **Start the Application**
   Spin up the entire stack using Docker Compose:
   ```bash
   docker-compose up --build -d
   ```

3. **Access Services**
   - **Frontend Dashboard**: http://localhost:8081
   - **Backend API**: http://localhost:8080
   - **MongoDB**: mongodb://localhost:27017/trendzy
   - **Kafka**: localhost:9092

## Deployment to AWS

For production deployment on AWS:
1. Ensure your MongoDB Atlas connection string is updated in the `.env` file (`MONGODB_URI` / `MONGO_URI`).
2. Push your repository to GitHub. The included `.gitignore` keeps your secrets safe.
3. On your AWS EC2 instance, pull the code, configure the `.env` file, and deploy using:
   ```bash
   docker-compose up --build -d
   ```

3. **Access Services**
   - **Frontend**: http://localhost:8081
   - **Backend API**: http://localhost:8080
   - **MongoDB**: mongodb://localhost:27017/trendzy
   - **Kafka**: localhost:9092

*(Note: For strict production environments, you might want to configure an Nginx proxy or use AWS ECS/EKS).*
