#!/bin/bash

# Test script for microservices

echo "🚀 Testing Microservices with Jaeger Tracing"
echo "=============================================="

# Wait for services to be ready
echo "⏳ Waiting for services to start..."
sleep 10

# Function to check service health
check_health() {
    local service_name=$1
    local url=$2
    
    echo "🔍 Checking $service_name health..."
    response=$(curl -s -o /dev/null -w "%{http_code}" $url)
    
    if [ $response -eq 200 ]; then
        echo "✅ $service_name is healthy"
    else
        echo "❌ $service_name is not responding (HTTP $response)"
        return 1
    fi
}

# Check all services
echo ""
echo "📋 Health Checks:"
check_health "Order Service" "http://localhost:8080/health"
check_health "Payment Service" "http://localhost:8081/health"
check_health "Inventory Service" "http://localhost:8082/health"

echo ""
echo "🛒 Testing Order Creation..."

# Create a test order
order_response=$(curl -s -X POST http://localhost:8080/order \
  -H "Content-Type: application/json" \
  -d '{
    "customerName": "Test Customer",
    "productId": 1,
    "quantity": 2,
    "paymentMethod": "CREDIT_CARD"
  }')

echo "📄 Order Response:"
echo $order_response | jq '.' 2>/dev/null || echo $order_response

# Extract order ID if successful
order_id=$(echo $order_response | jq -r '.orderId' 2>/dev/null)

if [ "$order_id" != "null" ] && [ "$order_id" != "" ]; then
    echo ""
    echo "🔍 Checking created order (ID: $order_id)..."
    
    order_details=$(curl -s http://localhost:8080/order/$order_id)
    echo "📄 Order Details:"
    echo $order_details | jq '.' 2>/dev/null || echo $order_details
else
    echo "❌ Order creation failed or order ID not found"
fi

echo ""
echo "📦 Checking Inventory for Product 1..."
inventory_response=$(curl -s http://localhost:8082/inventory/1)
echo "📄 Inventory Response:"
echo $inventory_response | jq '.' 2>/dev/null || echo $inventory_response

echo ""
echo "🔗 Access Points:"
echo "• Order Service: http://localhost:8080"
echo "• Payment Service: http://localhost:8081"
echo "• Inventory Service: http://localhost:8082"
echo "• Jaeger UI: http://localhost:16686"
echo ""
echo "🎯 To view traces:"
echo "1. Open http://localhost:16686"
echo "2. Select 'order-service' from the Service dropdown"
echo "3. Click 'Find Traces'"
echo "4. Click on a trace to see the full request flow"
echo ""
echo "✨ Test completed!"
