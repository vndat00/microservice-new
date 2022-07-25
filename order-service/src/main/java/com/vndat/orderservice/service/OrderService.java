package com.vndat.orderservice.service;

import com.vndat.orderservice.dto.InventoryResponse;
import com.vndat.orderservice.dto.OrderLineItemsDto;
import com.vndat.orderservice.dto.OrderRequest;
import com.vndat.orderservice.model.Order;
import com.vndat.orderservice.model.OrderLineItems;
import com.vndat.orderservice.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class OrderService {

    private final OrderRepository orderRepository;
    private final WebClient.Builder webClientBuilder;

    public String placeOrder(OrderRequest orderRequest){
        Order order = new Order();
        order.setOrderNumber(UUID.randomUUID().toString());
        List<OrderLineItems> orderLineItems = orderRequest.getOrderLineItemsDtoList()
                .stream()
                .map(this::mapToDto)
                .collect(Collectors.toList());
        order.setLineItems(orderLineItems);

        List<String> skuCodes = order.getLineItems().stream()
                .map(OrderLineItems::getSkuCode)
                .collect(Collectors.toList());

        // Call Invention Service, and place order if the product is in stock
        InventoryResponse[] inventoryResponses = webClientBuilder.build().get()
                .uri("http://inventory-service/api/v1/inventory",
                        // Vì sử dụng localhost:8082 là hardcode nên thay bằng tên của inventory service application
                        uriBuilder -> uriBuilder.queryParam("sku-code", skuCodes).build())
                .retrieve()
                .bodyToMono(InventoryResponse[].class)
                .block();
        boolean allProductInStock = Arrays.stream(inventoryResponses).allMatch(InventoryResponse::getIsInStock);

        if (allProductInStock){
            orderRepository.save(order);
            return "Order Placed Successfully!";
        } else {
            throw new IllegalArgumentException("Product is not in stock. Please try again later!");
        }
    }

    private OrderLineItems mapToDto(OrderLineItemsDto orderLineItemsDto) {
        OrderLineItems orderLineItems = new OrderLineItems();
        orderLineItems.setPrice(orderLineItemsDto.getPrice());
        orderLineItems.setSkuCode(orderLineItemsDto.getSkuCode());
        orderLineItems.setQuantity(orderLineItemsDto.getQuantity());
        return orderLineItems;
    }
}
