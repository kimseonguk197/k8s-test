package com.example.ordersystem.ordering.dto;


import com.example.ordersystem.ordering.domain.Ordering;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderSaveReqDto {
        private Long productId;
        private Integer productCount;
}
