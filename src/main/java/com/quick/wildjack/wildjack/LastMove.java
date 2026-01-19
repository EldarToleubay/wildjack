package com.quick.wildjack.wildjack;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class LastMove {
    private int x;
    private int y;
    private String playerId;
    @JsonProperty("isJackRemove")
    private boolean isJackRemove;
    @JsonProperty("isJackWild")
    private boolean isJackWild;
    private Card card;
}
