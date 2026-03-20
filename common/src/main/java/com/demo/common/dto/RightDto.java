package com.demo.common.dto;

import lombok.*;

import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RightDto {
    private UUID id;
    private String name;
    private String description;
}
