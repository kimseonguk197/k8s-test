package com.example.ordersystem.member.domain;

import com.example.ordersystem.common.domain.Address;
import com.example.ordersystem.common.domain.BaseTimeEntity;
import com.example.ordersystem.member.dto.MemberResDto;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import javax.persistence.*;
import java.util.List;

@Entity
@Getter
@NoArgsConstructor
@Builder
@AllArgsConstructor
public class Member extends BaseTimeEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;
    @Column(nullable = false, unique = true)
    private String email;
    private String password;
    @Embedded
    private Address address;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    private Role role = Role.USER;

    public MemberResDto fromEntity(){
        return MemberResDto.builder().id(this.id)
                .name(this.name)
                .email(this.email)
                .address(this.address)
                .build();
    }

    public void updatePassword(String password){
        this.password = password;
    }
}
