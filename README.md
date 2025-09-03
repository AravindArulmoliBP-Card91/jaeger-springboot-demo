# Microservices with Jaeger Distributed Tracing Demo

This project demonstrates a microservices architecture with distributed tracing using Jaeger and OpenTelemetry. It consists of three Spring Boot microservices that interact with each other, a MySQL database, Redis cache, and Jaeger for distributed tracing.

## Architecture

### Services
- **order-service** (Port 8080): Handles order creation and orchestrates the order flow
- **payment-service** (Port 8081): Processes payments and reserves inventory
- **inventory-service** (Port 8082): Manages product inventory

### Infrastructure
- **MySQL**: Central database for all services
- **Redis**: Caching layer for improved performance
- **Jaeger**: Distributed tracing system
- **OpenTelemetry Java Agent**: Automatic instrumentation for tracing

### Service Flow
```
order-service → payment-service → inventory-service
     ↓               ↓                    ↓
   MySQL           MySQL              MySQL
     ↓               ↓                    ↓
   Redis           Redis              Redis
```

## Prerequisites

- Docker and Docker Compose
- Java 21 (for local development)
- Maven (for local development)

## Quick Start

1. **Clone and navigate to the project directory**
   ```bash
   git clone <repository-url>
   cd jaeger-demo
   ```

2. **Start all services**
   ```bash
   docker-compose up --build
   ```

   This will start:
   - MySQL database (port 3306)
   - Redis cache (port 6379)
   - Jaeger UI (port 16686)
   - inventory-service (port 8082)
   - payment-service (port 8081)
   - order-service (port 8080)

3. **Wait for services to be ready**
   
   Check service health:
   ```bash
   curl http://localhost:8080/health  # Order Service
   curl http://localhost:8081/health  # Payment Service
   curl http://localhost:8082/health  # Inventory Service
   ```

## Testing the Application

### 1. Create an Order

```bash
curl -X POST http://localhost:8080/order \
  -H "Content-Type: application/json" \
  -d '{
    "customerName": "John Doe",
    "productId": 1,
    "quantity": 2,
    "paymentMethod": "CREDIT_CARD"
  }'
```

Expected response:
```json
{
  "success": true,
  "message": "Order processed successfully",
  "orderId": 1,
  "status": "COMPLETED",
  "totalAmount": 200.00,
  "paymentTransactionId": "uuid-string"
}
```

### 2. Check Order Status

```bash
curl http://localhost:8080/order/1
```

### 3. Check Inventory

```bash
curl http://localhost:8082/inventory/1
```

### 4. View Distributed Traces

1. Open Jaeger UI: http://localhost:16686
2. Select service: `order-service`, `payment-service`, or `inventory-service`
3. Click "Find Traces"
4. Click on a trace to see the complete request flow across all services

## Sample Data

The MySQL database is initialized with sample inventory data:

| Product ID | Product Name | Quantity Available | Unit Price |
|------------|--------------|-------------------|------------|
| 1          | Laptop       | 50                | $999.99    |
| 2          | Mouse        | 100               | $29.99     |
| 3          | Keyboard     | 75                | $79.99     |
| 4          | Monitor      | 25                | $299.99    |
| 5          | Headphones   | 80                | $149.99    |

## API Endpoints

### Order Service (Port 8080)
- `POST /order` - Create a new order
- `GET /order/{id}` - Get order by ID
- `GET /health` - Health check

### Payment Service (Port 8081)
- `POST /pay` - Process payment (internal)
- `GET /health` - Health check

### Inventory Service (Port 8082)
- `POST /reserve` - Reserve inventory (internal)
- `GET /inventory/{productId}` - Get inventory by product ID
- `GET /health` - Health check

## Tracing Features

The application demonstrates:

1. **Cross-service tracing**: Traces span across all three microservices
2. **Database operations**: JPA queries are automatically traced
3. **Redis operations**: Cache operations are traced
4. **HTTP calls**: RestTemplate calls between services are traced
5. **Error tracking**: Failed requests and exceptions are captured

## Database Schema

### Orders Table
```sql
CREATE TABLE orders (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    customer_name VARCHAR(100) NOT NULL,
    product_id BIGINT NOT NULL,
    quantity INT NOT NULL,
    total_amount DECIMAL(10,2) NOT NULL,
    status VARCHAR(50) DEFAULT 'PENDING',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);
```

### Payments Table
```sql
CREATE TABLE payments (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_id BIGINT NOT NULL,
    amount DECIMAL(10,2) NOT NULL,
    payment_method VARCHAR(50) NOT NULL,
    status VARCHAR(50) DEFAULT 'PENDING',
    transaction_id VARCHAR(100),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);
```

### Inventory Table
```sql
CREATE TABLE inventory (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    product_id BIGINT NOT NULL UNIQUE,
    product_name VARCHAR(100) NOT NULL,
    quantity_available INT NOT NULL DEFAULT 0,
    reserved_quantity INT NOT NULL DEFAULT 0,
    unit_price DECIMAL(10,2) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);
```

## Redis Caching

The application uses Redis for:

1. **Order caching**: Orders are cached for 24 hours
2. **Customer order counts**: Tracks order frequency per customer
3. **Inventory checks**: Caches inventory availability checks
4. **Reservation tracking**: Tracks reserved inventory

## Troubleshooting

### Services won't start
- Ensure Docker is running
- Check if ports 3306, 6379, 8080-8082, 16686 are available
- Wait for MySQL health check to pass before services start

### Traces not appearing in Jaeger
- Verify Jaeger is running: http://localhost:16686
- Check service logs for OpenTelemetry agent errors
- Ensure OTEL_EXPORTER_OTLP_ENDPOINT is correctly configured

### Database connection errors
- Wait for MySQL to fully initialize (check logs)
- Verify database credentials in docker-compose.yml

## Development

To run services locally for development:

1. Start infrastructure:
   ```bash
   docker-compose up mysql redis jaeger
   ```

2. Run each service with the OpenTelemetry agent:
   ```bash
   java -javaagent:opentelemetry-javaagent.jar \
        -Dotel.service.name=order-service \
        -Dotel.exporter.otlp.endpoint=http://localhost:4317 \
        -jar order-service/target/order-service-1.0.0.jar
   ```

## Cleanup

To stop and remove all containers:
```bash
docker-compose down -v
```

The `-v` flag removes volumes, including the MySQL data.

## Next Steps

- Add more complex business logic
- Implement circuit breakers with Resilience4j
- Add metrics collection with Prometheus
- Implement event-driven architecture with message queues
- Add API Gateway with Spring Cloud Gateway
