package com.quick.wildjack.wildjack;

import lombok.Data;

import java.util.List;

@Data
public class Player {
    private String id;
    private String name;
    private String color;       // цвет фишки
    private List<Card> hand;    // карты на руке
}
