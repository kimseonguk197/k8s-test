package com.example.ordersystem.ordering.service;

import com.example.ordersystem.common.dto.CommonResDto;
import com.example.ordersystem.common.service.StockInventoryService;
import com.example.ordersystem.ordering.controller.SseController;
import com.example.ordersystem.ordering.domain.OrderDetail;
import com.example.ordersystem.ordering.domain.OrderStatus;
import com.example.ordersystem.ordering.domain.Ordering;
import com.example.ordersystem.ordering.dto.*;
import com.example.ordersystem.ordering.repository.OrderDetailRepository;
import com.example.ordersystem.ordering.repository.OrderingRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
//import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import javax.persistence.EntityNotFoundException;
import java.util.ArrayList;
import java.util.List;

@Service
@Transactional
public class OrderingService {
    private final OrderingRepository orderingRepository;
    private final OrderDetailRepository orderDetailRepository;
    private final StockInventoryService stockInventoryService;
//    private final StockDecreaseEventHandler stockDecreaseEventHandler;
    private final SseController sseController;
    private final RestTemplate restTemplate;

    private final ProductFeign productFeign;

//    private final KafkaTemplate<String, Object> kafkaTemplate;
    @Autowired
    public OrderingService(OrderingRepository orderingRepository, OrderDetailRepository orderDetailRepository, StockInventoryService stockInventoryService, SseController sseController, RestTemplate restTemplate, ProductFeign productFeign) {
        this.orderingRepository = orderingRepository;
        this.orderDetailRepository = orderDetailRepository;
        this.stockInventoryService = stockInventoryService;
//        this.stockDecreaseEventHandler = stockDecreaseEventHandler;
        this.sseController = sseController;
        this.restTemplate = restTemplate;
        this.productFeign = productFeign;
//        this.kafkaTemplate = kafkaTemplate;
    }

//    syncronized를 설정한다 하더라도, 재고 감소가 DB에 반영되는 시점은 트랜잭션이 커밋되고 종료되는 시점
    public  Ordering orderRestTemplateCreate(List<OrderSaveReqDto> dtos){
//        방법2.JPA에 최적화된 방식
//        Member member = memberRepository.findById(dto.getMemberId()).orElseThrow(()->new EntityNotFoundException("없음"));
        String memberEmail = SecurityContextHolder.getContext().getAuthentication().getName();
        Ordering ordering = Ordering.builder()
                .memberEmail(memberEmail)
                .build();
        for(OrderSaveReqDto dto : dtos){
            int quantity = dto.getProductCount();
//            Product API에 요청을 통해 product객체를 조회해야함
            String productGetUrl = "http://product-service/product/"+dto.getProductId();
            HttpHeaders httpHeaders = new HttpHeaders();
            String token = (String) SecurityContextHolder.getContext().getAuthentication().getCredentials();
            httpHeaders.set("Authorization", token);
            HttpEntity<String> entity = new HttpEntity<>(httpHeaders);
            ResponseEntity<CommonResDto> productEntity = restTemplate.exchange(productGetUrl, HttpMethod.GET, entity, CommonResDto.class);
            ObjectMapper objectMapper = new ObjectMapper();
            ProductDto productDto = objectMapper.convertValue(productEntity.getBody().getResult(), ProductDto.class);
            System.out.println(productDto);
            if(productDto.getName().contains("sale")){
//            redis를 동한 재고관리 및 재고잔량 확인
                int newQuantity = stockInventoryService.decreaseStock(dto.getProductId(), dto.getProductCount()).intValue();
                if(newQuantity<0){
                    throw new IllegalArgumentException("재고 부족");
                }
////                rdb에 재고를 업데이트. rabbitmq를 통해 비동기적으로 이벤트 처리.
//                stockDecreaseEventHandler.publish(new StockDecreaseEvent(productDto.getId(), dto.getProductCount()));
            }else {
                if(productDto.getStockQuantity() < quantity){
                    throw new IllegalArgumentException("재고 부족");
                }
////                restTemplate을 통한 update요청
//                product.updateStockQuantity(quantity);
                String updateUrl = "http://product-service/product/updatestock";
                httpHeaders.setContentType(MediaType.APPLICATION_JSON);
                HttpEntity<ProductUpdateStockDto> updateEntity = new HttpEntity<>(
                        new ProductUpdateStockDto(dto.getProductId(), dto.getProductCount()), httpHeaders);
                restTemplate.exchange(updateUrl, HttpMethod.PUT, updateEntity, Void.class);
            }
            OrderDetail orderDetail =  OrderDetail.builder()
                    .productId(productDto.getId())
                    .quantity(quantity)
                    .ordering(ordering)
                    .build();
            ordering.getOrderDetails().add(orderDetail);
        }
        Ordering savedOrdering = orderingRepository.save(ordering);
        sseController.publishMessage(savedOrdering.fromEntity(), "admin@test.com");

        return savedOrdering;
    }

    public  Ordering orderFeignClientCreate(List<OrderSaveReqDto> dtos){
        String memberEmail = SecurityContextHolder.getContext().getAuthentication().getName();
        Ordering ordering = Ordering.builder()
                .memberEmail(memberEmail)
                .build();
        for(OrderSaveReqDto dto : dtos){
            int quantity = dto.getProductCount();
//            ResponseEntity가 기본응답값이므로 바로 CommonResDto로 매핑
            CommonResDto commonResDto =  productFeign.getProductById(dto.getProductId());
            ObjectMapper objectMapper = new ObjectMapper();
            ProductDto productDto = objectMapper.convertValue(commonResDto.getResult(), ProductDto.class);
            System.out.println(productDto);
            if(productDto.getName().contains("sale")){
//            redis를 동한 재고관리 및 재고잔량 확인
                int newQuantity = stockInventoryService.decreaseStock(dto.getProductId(), dto.getProductCount()).intValue();
                if(newQuantity<0){
                    throw new IllegalArgumentException("재고 부족");
                }
////                rdb에 재고를 업데이트. rabbitmq를 통해 비동기적으로 이벤트 처리.
//                stockDecreaseEventHandler.publish(new StockDecreaseEvent(productDto.getId(), dto.getProductCount()));
            }else {
                if(productDto.getStockQuantity() < quantity){
                    throw new IllegalArgumentException("재고 부족");
                }
                productFeign.updateProductStock(new ProductUpdateStockDto(dto.getProductId(), dto.getProductCount()));
            }
            OrderDetail orderDetail =  OrderDetail.builder()
                    .productId(productDto.getId())
                    .quantity(quantity)
                    .ordering(ordering)
                    .build();
            ordering.getOrderDetails().add(orderDetail);
        }
        Ordering savedOrdering = orderingRepository.save(ordering);
        sseController.publishMessage(savedOrdering.fromEntity(), "admin@test.com");

        return savedOrdering;
    }

//    public  Ordering orderFeignKafkaCreate(List<OrderSaveReqDto> dtos){
//        String memberEmail = SecurityContextHolder.getContext().getAuthentication().getName();
//        Ordering ordering = Ordering.builder()
//                .memberEmail(memberEmail)
//                .build();
//        for(OrderSaveReqDto dto : dtos){
//            int quantity = dto.getProductCount();
////            ResponseEntity가 기본응답값이므로 바로 CommonResDto로 매핑
//            CommonResDto commonResDto =  productFeign.getProductById(dto.getProductId());
//            ObjectMapper objectMapper = new ObjectMapper();
//            ProductDto productDto = objectMapper.convertValue(commonResDto.getResult(), ProductDto.class);
//            System.out.println(productDto);
//            if(productDto.getName().contains("sale")){
////            redis를 동한 재고관리 및 재고잔량 확인
//                int newQuantity = stockInventoryService.decreaseStock(dto.getProductId(), dto.getProductCount()).intValue();
//                if(newQuantity<0){
//                    throw new IllegalArgumentException("재고 부족");
//                }
////                rdb에 재고를 업데이트. rabbitmq를 통해 비동기적으로 이벤트 처리.
//                stockDecreaseEventHandler.publish(new StockDecreaseEvent(productDto.getId(), dto.getProductCount()));
//            }else {
//                if(productDto.getStockQuantity() < quantity){
//                    throw new IllegalArgumentException("재고 부족");
//                }
//                ProductUpdateStockDto productUpdateStockDto = new ProductUpdateStockDto(dto.getProductId(), dto.getProductCount());
//                kafkaTemplate.send("product-update-topic", productUpdateStockDto);
//            }
//            OrderDetail orderDetail =  OrderDetail.builder()
//                    .productId(productDto.getId())
//                    .quantity(quantity)
//                    .ordering(ordering)
//                    .build();
//            ordering.getOrderDetails().add(orderDetail);
//        }
//        Ordering savedOrdering = orderingRepository.save(ordering);
//        sseController.publishMessage(savedOrdering.fromEntity(), "admin@test.com");
//
//        return savedOrdering;
//    }

    public List<OrderListResDto> orderList(){
        List<Ordering> orderings = orderingRepository.findAll();
        List<OrderListResDto> orderListResDtos = new ArrayList<>();
        for(Ordering ordering : orderings){
            orderListResDtos.add(ordering.fromEntity());
        }
        return  orderListResDtos;
    }

    public List<OrderListResDto> myOrders(){
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        List<Ordering> orderings = orderingRepository.findByMemberEmail(email);
        List<OrderListResDto> orderListResDtos = new ArrayList<>();
        for(Ordering ordering : orderings){
            orderListResDtos.add(ordering.fromEntity());
        }
        return  orderListResDtos;
    }

    public Ordering orderCancel(Long id){
        Ordering ordering = orderingRepository.findById(id).orElseThrow(()->new EntityNotFoundException("not found"));
        ordering.updateStatus(OrderStatus.CANCELED);
        return ordering;
    }
}
