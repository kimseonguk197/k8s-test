package com.example.ordersystem.ordering.domain;

import com.example.ordersystem.ordering.dto.OrderListResDto;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import javax.persistence.*;

@Entity
@Getter
@AllArgsConstructor
@Builder
@NoArgsConstructor
public class OrderDetail {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Integer quantity;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ordering_id")
    private Ordering ordering;


    private Long productId;

    public OrderListResDto.OrderDetailDto fromEntity(){
        OrderListResDto.OrderDetailDto orderDetailDto = OrderListResDto.OrderDetailDto
                .builder()
                .id(this.id)
                .count(this.quantity)
                .build();
        return orderDetailDto;
    }
}
