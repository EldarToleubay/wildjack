package com.quick.wildjack.wildjack;

import lombok.Data;

@Data
public class Cell {
    private Card card;       // карта на клетке
    private Player owner;    // кто поставил фишку
    private boolean isCorner; // corners бесплатные
}
