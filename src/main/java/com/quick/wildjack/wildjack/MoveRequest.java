package com.quick.wildjack.wildjack;

import lombok.Data;

@Data
public class MoveRequest {
    private Card card;
    private int x;
    private int y;
}
