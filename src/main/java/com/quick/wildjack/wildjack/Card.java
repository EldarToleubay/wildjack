package com.quick.wildjack.wildjack;

import lombok.Data;

@Data
public class Card {
    private String suit; // Hearts, Diamonds, Clubs, Spades
    private String rank; // 2..10, J, Q, K, A
}
